package com.cnblogs.hoojo.fabric.sdk.updated;

import static java.lang.String.format;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.UpdateChannelConfiguration;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;
import org.junit.Before;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.cnblogs.hoojo.fabric.sdk.util.GzipUtils;

/**
 * Update channel scenario
 * See http://hyperledger-fabric.readthedocs.io/en/master/configtxlator.html
 * for details.
 */
public class UpgradeChannelTest {

    private static final DefaultConfiguration config = DefaultConfiguration.getConfig();
    
    private static final String CONFIGTXLATOR_LOCATION = config.getFabricConfigTxLaterURL();

    private static final String ORIGINAL_BATCH_TIMEOUT = "\"timeout\": \"2s\""; // Batch time out in configtx.yaml
    private static final String UPDATED_BATCH_TIMEOUT = "\"timeout\": \"5s\"";  // What we want to change it to.

    private static final String FOO_CHANNEL_NAME = "foo";

    private Collection<Organization> organizations;

    @Before
    public void checkConfig() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException {

        out("\n\n\nRUNNING: UpdateChannelIT\n");
        //resetConfig();
        //configHelper.customizeConfig();

        organizations = config.getOrganizations();
    }

    @Test
    public void setup() {
        try {
            ////////////////////////////
            // Setup client

            out("Create instance of client.");
            HFClient client = HFClient.createNewInstance();

            client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            ////////////////////////////
            //Set up KeyValueFileStore
            out("Create keyValue file store system");
            File storeFile = new File("HFCSampletest.properties");
            storeFile.deleteOnExit();

            final KeyValueFileStore store = new KeyValueFileStore(storeFile);

            ////////////////////////////
            //Set up USERS
            out("get users for all orgs");
            for (Organization org : organizations) {
                //SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface
                OrganizationUser peerAdmin = wrapperPeerAdmin(store, org);
                org.setPeerAdmin(peerAdmin);
            }

            ////////////////////////////
            //Set up Channel
            out("Reconstruct and run the channels");
            Organization org = config.getOrganization("peerOrg1");
            
            Channel fooChannel = reconstructChannel(FOO_CHANNEL_NAME, client, org);

            // Getting foo channels current configuration bytes.
            out("preview channel configuration: %s", fooChannel.getName());
            final byte[] channelConfigurationBytes = fooChannel.getChannelConfigurationBytes();

            HttpClient httpclient = HttpClients.createDefault();
            
            HttpPost httppost = new HttpPost(CONFIGTXLATOR_LOCATION + "/protolator/decode/common.Config");
            httppost.setEntity(new ByteArrayEntity(channelConfigurationBytes));

            HttpResponse response = httpclient.execute(httppost);
            int statuscode = response.getStatusLine().getStatusCode();
            out("Got %s status for decoding current channel config bytes", statuscode);
            assertEquals(200, statuscode);

            String responseAsString = EntityUtils.toString(response.getEntity());
            out("channel %s configuration to JSON: %s", fooChannel.getName(), responseAsString);
            
            //responseAsString is JSON but use just string operations for this test.
            if (!responseAsString.contains(ORIGINAL_BATCH_TIMEOUT)) {
                fail(format("Did not find expected batch timeout '%s', in:%s", ORIGINAL_BATCH_TIMEOUT, responseAsString));
            }

            //Now modify the batch timeout
            out("converter channel configuration to BYTE: %s", fooChannel.getName());
            String updateString = responseAsString.replace(ORIGINAL_BATCH_TIMEOUT, UPDATED_BATCH_TIMEOUT);

            httppost = new HttpPost(CONFIGTXLATOR_LOCATION + "/protolator/encode/common.Config");
            httppost.setEntity(new StringEntity(updateString));

            response = httpclient.execute(httppost);
            statuscode = response.getStatusLine().getStatusCode();
            out("Got %s status for encoding the new desired channel config bytes", statuscode);
            assertEquals(200, statuscode);
            
            byte[] newConfigBytes = EntityUtils.toByteArray(response.getEntity());

            // Now send to configtxlator multipart form post with original config bytes, updated config bytes and channel name.
            out("get update channel configuration BYTE: %s", fooChannel.getName());
            httppost = new HttpPost(CONFIGTXLATOR_LOCATION + "/configtxlator/compute/update-from-configs");

            HttpEntity multipartEntity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody("original", channelConfigurationBytes, ContentType.APPLICATION_OCTET_STREAM, "originalFakeFilename")
                    .addBinaryBody("updated", newConfigBytes, ContentType.APPLICATION_OCTET_STREAM, "updatedFakeFilename")
                    .addBinaryBody("channel", fooChannel.getName().getBytes()).build();

            httppost.setEntity(multipartEntity);

            response = httpclient.execute(httppost);
            statuscode = response.getStatusLine().getStatusCode();
            out("Got %s status for updated config bytes needed for updateChannelConfiguration ", statuscode);
            assertEquals(200, statuscode);

            byte[] updateBytes = EntityUtils.toByteArray(response.getEntity());
            
            // 构建更新配置对象
            UpdateChannelConfiguration updateChannelConfiguration = new UpdateChannelConfiguration(updateBytes);

            // 要更改通道，我们需要使用orderer管理员确认
            // private key: src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/ordererOrganizations/example.com/users/Admin@example.com/msp/keystore/f1a9a940f57419a18a83a852884790d59b378281347dd3d4a88c2b820a0f70c9_sk
            // certificate:  src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/ordererOrganizations/example.com/users/Admin@example.com/msp/signcerts/Admin@example.com-cert.pem
            File privateKeyFile = GzipUtils.findFileSk(Paths.get("src/test/fixture/sdkintegration/e2e-2Orgs/" + config.getFabricConfigGeneratorVersion() + "/crypto-config/ordererOrganizations/example.com/users/Admin@example.com/msp/keystore/").toFile());
            File certificateFile = Paths.get("src/test/fixture/sdkintegration/e2e-2Orgs/" + config.getFabricConfigGeneratorVersion() + "/crypto-config/ordererOrganizations/example.com/users/Admin@example.com/msp/signcerts/Admin@example.com-cert.pem").toFile();
            final String orgName = org.getName();
            final OrganizationUser ordererAdmin = store.getMember(orgName + "OrderAdmin", orgName, "OrdererMSP", privateKeyFile, certificateFile);

            // 设置 orderer 管理员
            client.setUserContext(ordererAdmin);

            out("execute update channel configuration: %s", fooChannel.getName());
            byte[] signature = client.getUpdateChannelConfigurationSignature(updateChannelConfiguration, ordererAdmin);
            //Ok now do actual channel update.
            fooChannel.updateChannelConfiguration(updateChannelConfiguration, signature);

            //让我们添加一些额外的验证...
            client.setUserContext(org.getPeerAdmin());

            out("Get updated channel configuration: %s", fooChannel.getName());
            final byte[] modChannelBytes = fooChannel.getChannelConfigurationBytes();

            //Now decode the new channel config bytes to json...
            httppost = new HttpPost(CONFIGTXLATOR_LOCATION + "/protolator/decode/common.Config");
            httppost.setEntity(new ByteArrayEntity(modChannelBytes));

            response = httpclient.execute(httppost);
            statuscode = response.getStatusLine().getStatusCode();
            assertEquals(200, statuscode);

            responseAsString = EntityUtils.toString(response.getEntity());
            out("channel %s configuration to JSON: %s", fooChannel.getName(), responseAsString);
            
            if (!responseAsString.contains(UPDATED_BATCH_TIMEOUT)) {
                //If it doesn't have the updated time out it failed.
                fail(format("Did not find updated expected batch timeout '%s', in:%s", UPDATED_BATCH_TIMEOUT, responseAsString));
            }
            if (responseAsString.contains(ORIGINAL_BATCH_TIMEOUT)) { //Should not have been there anymore!
                fail(format("Found original batch timeout '%s', when it was not expected in:%s", ORIGINAL_BATCH_TIMEOUT, responseAsString));
            }

            out("\n");

            Thread.sleep(3000); // give time for events to happen

            assertTrue(eventCountFilteredBlock > 0); // make sure we got blockevent that were tested.
            assertTrue(eventCountBlock > 0); // make sure we got blockevent that were tested.

            out("That's all folks!");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    /**
	 * peer节点管理员证书私钥封装
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:53:35
	 * @throws IOException
	 */
	private OrganizationUser wrapperPeerAdmin(KeyValueFileStore store, Organization org) throws EnrollmentException, InvalidArgumentException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, IOException {
		out("节点管理员——认证……");
		
		final String orgName = org.getName();
		final String mspid = org.getMSPID();
		final String domain = org.getDomainName();
		
		// src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/
		File keydir = Paths.get(config.getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/keystore", domain)).toFile();
		File privateKeyFile = GzipUtils.findFileSk(keydir);
		File certificateFile = Paths.get(config.getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", domain, domain)).toFile();

		// 从缓存或store中获取用户
		OrganizationUser peerAdmin = store.getMember(orgName + "Admin", orgName, mspid, privateKeyFile, certificateFile);
		out("构建Peer Admin用户：%s", peerAdmin);
		
		return peerAdmin;
	}

    int eventCountFilteredBlock = 0;
    int eventCountBlock = 0;

    private Channel reconstructChannel(String name, HFClient client, Organization org) throws Exception {

        client.setUserContext(org.getPeerAdmin());
        
        out("Create new Channel : %s", name);
        Channel newChannel = client.newChannel(name);

        // Create new Orderer & Orderer to Channel
        for (String orderName : org.getOrdererNames()) {
        	out("Create new Orderer: %s & add Orderer to Channel: %s", orderName, name);
            newChannel.addOrderer(client.newOrderer(orderName, org.getOrdererLocation(orderName), config.getOrdererProperties(orderName)));
        }

        assertTrue(org.getPeerNames().size() > 1); // need at least two for testing.

        
        int i = 0;
        // add peer to channel
        for (String peerName : org.getPeerNames()) {
            String peerLocation = org.getPeerLocation(peerName);
            Peer peer = client.newPeer(peerName, peerLocation, config.getPeerProperties(peerName));

            //Query the actual peer for which channels it belongs to and check it belongs to this channel
            Set<String> channels = client.queryChannels(peer);
            if (!channels.contains(name)) {
                throw new AssertionError(format("Peer %s does not appear to belong to channel %s", peerName, name));
            }
            
            Channel.PeerOptions peerOptions = createPeerOptions().setPeerRoles(EnumSet.of(Peer.PeerRole.CHAINCODE_QUERY, Peer.PeerRole.ENDORSING_PEER, Peer.PeerRole.LEDGER_QUERY, Peer.PeerRole.EVENT_SOURCE));
            if (i % 2 == 0) {
                peerOptions.registerEventsForFilteredBlocks(); // we need a mix of each type for testing.
            } else {
                peerOptions.registerEventsForBlocks();
            }
            ++i;

            out("create peer: %s & add peer to channel: name", peerName, name);
            newChannel.addPeer(peer, peerOptions);
        }

        // add eventHub to channel
        for (String eventHubName : org.getEventHubNames()) {
            EventHub eventHub = client.newEventHub(eventHubName, org.getEventHubLocation(eventHubName), config.getEventHubProperties(eventHubName));
            
            out("create eventHub: %s & add eventHub to channel: name", eventHubName, name);
            newChannel.addEventHub(eventHub);
        }

        //用于测试不是交易的块
        newChannel.registerBlockListener(blockEvent -> {
            // 注意对等事件总是从发送最后一个块开始，因此这将得到最后一个区块
            int transactions = 0;
            int nonTransactions = 0;
            for (BlockInfo.EnvelopeInfo envelopeInfo : blockEvent.getEnvelopeInfos()) {

                if (BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE == envelopeInfo.getType()) {
                    ++transactions;
                } else {
                    assertEquals(BlockInfo.EnvelopeType.ENVELOPE, envelopeInfo.getType());
                    ++nonTransactions;
                }

            }
            out("nontransactions %d, transactions %d", nonTransactions, transactions);
            
            assertTrue(format("nontransactions %d, transactions %d", nonTransactions, transactions), nonTransactions < 2); // non transaction blocks only have one envelope
            assertTrue(format("nontransactions %d, transactions %d", nonTransactions, transactions), nonTransactions + transactions > 0); // has to be one.
            assertFalse(format("nontransactions %d, transactions %d", nonTransactions, transactions), nonTransactions > 0 && transactions > 0); // can't have both.

            if (nonTransactions > 0) { // 这是一个更新块 - 不要在乎这里的其他业务

                if (blockEvent.isFiltered()) {
                    ++eventCountFilteredBlock; // 确保我们看到非交易事件
                } else {
                    ++eventCountBlock;
                }
                
                assertEquals(0, blockEvent.getTransactionCount());
                assertEquals(1, blockEvent.getEnvelopeCount());
                
                for (@SuppressWarnings("unused") TransactionEvent transactionEvent : blockEvent.getTransactionEvents()) {
                    fail("Got transaction event in a block update"); // 上面的业务都是更新事件，不应该有交易发生
                }
            }
        });

        newChannel.initialize();

        return newChannel;
    }

    static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();
    }
}
