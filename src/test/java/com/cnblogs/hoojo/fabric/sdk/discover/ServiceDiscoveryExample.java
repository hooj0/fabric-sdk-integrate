package com.cnblogs.hoojo.fabric.sdk.discover;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.hyperledger.fabric.sdk.Channel.DiscoveryOptions.createDiscoveryOptions;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Channel.DiscoveryOptions;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.Peer.PeerRole;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.ServiceDiscovery;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.junit.Ignore;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;

/**
 * service discovery example
 * 服务发现示例，需要在 End2EndExamples 示例运行后，执行本示例
 * 
 * @author hoojo
 * @createDate 2018年12月6日 下午3:36:47
 * @file ServiceDiscoveryExample.java
 * @package com.cnblogs.hoojo.fabric.sdk.discover
 * @project fabric-sdk-integrate
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class ServiceDiscoveryExample {
	
	private static final DefaultConfiguration CONFIG = DefaultConfiguration.getConfig();

	private static final String FOO_CHANNEL_NAME = "foo";
	private static final String ORG_NAME = "Org1";
	
	private static final String ADMIN_NAME = "admin";
	private static final String USER_NAME = "user1";

	private static final Type CHAIN_CODE_LANG = Type.GO_LANG;
	private static final String CHAIN_CODE_NAME = "example_cc_go";
    private static final ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).build();
    
    private static KeyValueFileStore store;
    private static final File storeFile = new File("HFCSampletest.properties");

    
    		 // 除非您编辑主机文件，否则服务发现报告的主机名将不起作用
    //@Ignore  //Hostnames reported by service discovery won't work unless you edit hostfile
    @Test
    public void setup() throws Exception {
    	out("\n\n\nRUNNING: %s.\n", "ServiceDiscovery Example");
    	// Persistence is not part of SDK. Sample file store is for demonstration purposes only!
    	// 持久性不是SDK的一部分。 示例文件存储仅用于演示目的！
        // MUST be replaced with more robust application implementation  (Database, LDAP)
    	// 必须用更强大的应用程序实现（数据库，LDAP）替换
    	
    	store = new KeyValueFileStore(storeFile);

    	String version = CONFIG.getFabricConfigGeneratorVersion();
        
        Organization org = CONFIG.getOrganization(ORG_NAME);
        @SuppressWarnings("unused")
		//OrganizationUser peerAdmin = store.getMember(ADMIN_NAME, org.getName());
        OrganizationUser user = store.getMember(USER_NAME, org.getName());

        final HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        client.setUserContext(user);
        
        final String protocol = CONFIG.isRunningFabricTLS() ? "grpcs:" : "grpc:";
        Properties peerProps = CONFIG.getPeerProperties("peer0.org1.example.com");

        Properties discoverProps = new Properties();

        //Create initial discovery peer. 创建初始发现对等体。
        //==================================================================
        Channel channel = client.newChannel(FOO_CHANNEL_NAME); //create channel that will be discovered. 创建将被发现的频道。

        Peer discoveryPeer = client.newPeer("peer0.org1.example.com", protocol + "//localhost:7051", peerProps);
        EnumSet<PeerRole> peerRoles = EnumSet.of(PeerRole.SERVICE_DISCOVERY, PeerRole.LEDGER_QUERY, PeerRole.EVENT_SOURCE, PeerRole.CHAINCODE_QUERY);
        channel.addPeer(discoveryPeer, createPeerOptions().setPeerRoles(peerRoles));

        // Need to provide client TLS certificate and key files when running mutual tls.
        // 运行相互tls时需要提供客户端TLS证书和密钥文件。
        if (CONFIG.isRunningFabricTLS()) {
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.default.clientCertFile", "src/test/fixture/sdkintegration/e2e-2Orgs/" + version + "/crypto-config/peerOrganizations/org1.example.com/users/User1@org1.example.com/tls/client.crt");
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.default.clientKeyFile", "src/test/fixture/sdkintegration/e2e-2Orgs/" + version + "/crypto-config/peerOrganizations/org1.example.com/users/User1@org1.example.com/tls/client.key");

            // Need to do host name override for true tls in testing environment
            // 需要在测试环境中对true tls执行主机名覆盖
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.endpoint.hostnameOverride.localhost:7050", "orderer.example.com");
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.endpoint.hostnameOverride.localhost:7051", "peer0.org1.example.com");
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.endpoint.hostnameOverride.localhost:7056", "peer1.org1.example.com");
        } else {
            discoverProps.put("org.hyperledger.fabric.sdk.discovery.default.protocol", "grpc:");
        }
        channel.setServiceDiscoveryProperties(discoverProps);

        final byte[] bytes = channel.serializeChannel(); // Next 3 lines are for testing purposes only! 接下来的3行仅用于测试目的！
        channel.shutdown(false);
        channel = client.deSerializeChannel(bytes);
        channel.initialize(); // initialize the channel.

        
        Set<String> expect = new HashSet<>(Arrays.asList(protocol + "//orderer.example.com:7050")); //discovered orderer
        for (Orderer orderer : channel.getOrderers()) {
            expect.remove(orderer.getUrl());
        }
        assertTrue(expect.isEmpty());

        final Collection<String> discoveredChaincodeNames = channel.getDiscoveredChaincodeNames();
        for (String chaincodeName : discoveredChaincodeNames) {
        	System.out.println("discover chaincode: " + chaincodeName);
        }
        assertTrue(discoveredChaincodeNames.contains(CHAIN_CODE_NAME));

        // Send transaction proposal to all peers
        //=====================================================================
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
        transactionProposalRequest.setFcn("move");
        transactionProposalRequest.setProposalWaitTime(CONFIG.getProposalWaitTime());
        transactionProposalRequest.setArgs("a", "b", "1");

        //Send proposal request discovering the what endorsers (peers) are needed. 发送提案请求，发现需要什么背书人（同行）。
        DiscoveryOptions discoveryOptions = createDiscoveryOptions().
        		setEndorsementSelector(ServiceDiscovery.EndorsementSelector.ENDORSEMENT_SELECTION_RANDOM) // 通过随机布局组和随机代言人进行背书选择。
        		.setForceDiscovery(true);
        // 发送交易并背书
        Collection<ProposalResponse> proposalResponses = channel.sendTransactionProposalToEndorsers(transactionProposalRequest, discoveryOptions);
        assertFalse(proposalResponses.isEmpty()); // 交易成功

        // 再次发送交易
        transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
        transactionProposalRequest.setFcn("move");
        transactionProposalRequest.setProposalWaitTime(CONFIG.getProposalWaitTime());
        transactionProposalRequest.setArgs("a", "b", "1");

        //Send proposal request discovering the what endorsers (peers) are needed.
        proposalResponses = channel.sendTransactionProposalToEndorsers(transactionProposalRequest,
                createDiscoveryOptions().ignoreEndpoints("blah.blah.blah.com:90", "blah.com:80",
                // aka peer0.org1.example.com our discovery peer. Lets ignore it in endorsers selection and see if other discovered peer endorses.
        		// 名peer0.org1.example.com是我们的发现同行。 让我们在代言人的选择中忽略它，看看其他被发现的同伴是否赞同
                "peer0.org1.example.com:7051")
                // if chaincode makes additional chaincode calls or uses collections you should add them with setServiceDiscoveryChaincodeInterests
                // 如果chaincode进行额外的链代码调用或使用集合，则应使用setServiceDiscoveryChaincodeInterests添加它们
                //.setServiceDiscoveryChaincodeInterests(Channel.ServiceDiscoveryChaincodeCalls.createServiceDiscoveryChaincodeCalls("someOtherChaincodeName").addCollections("collection1", "collection2"))
                );
        assertEquals(proposalResponses.size(), 1); // 1个交易结果
        
        final ProposalResponse proposalResponse = proposalResponses.iterator().next();
        final Peer peer = proposalResponse.getPeer();
        assertEquals(protocol + "//peer1.org1.example.com:7056", peer.getUrl()); // not our discovery peer but the discovered one.

        String expectedTransactionId = null;
        for (ProposalResponse response : proposalResponses) {
            expectedTransactionId = response.getTransactionID();
            if (response.getStatus() != ProposalResponse.Status.SUCCESS || !response.isVerified()) {
                fail("Failed status bad endorsement");
            }
        }
        
        final StringBuilder evenTransactionId = new StringBuilder();
        //Send it to the orderer that was discovered. 将交易发送给已发现的订货人。
        //==========================================================================
        channel.sendTransaction(proposalResponses).thenApply(transactionEvent -> {
            evenTransactionId.setLength(0);
            evenTransactionId.append(transactionEvent.getTransactionID());
            return null;
        }).exceptionally(e -> {
            if (e instanceof TransactionEventException) {
                BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                if (te != null) {
                    throw new AssertionError(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()), e);
                }
            }

            throw new AssertionError(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()), e);
        }).get(CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);

        // 交易id一致
        assertEquals(expectedTransactionId, evenTransactionId.toString());
        out("That's all folks!");
    }

    static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();
    }
}