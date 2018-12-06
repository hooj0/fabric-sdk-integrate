package com.cnblogs.hoojo.fabric.sdk.examples;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.peer.Query.ChaincodeInfo;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Channel.PeerOptions;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.Peer.PeerRole;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TransactionRequest;
import org.hyperledger.fabric.sdk.UpgradeProposalRequest;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.junit.Before;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.cnblogs.hoojo.fabric.sdk.util.TestUtils;
import com.google.gson.Gson;


/**
 * `end 2 end` JavaSDK use API End2EndAndBackAgainExamples
 * 在End2End示例运行完成后，不重新启动网络，会从前一个示例中恢复数据，运行示例。
 * 
 * 重新创建频道。
 * 更新链码。
 * 检查已安装和实例化的链代码。
 * 
 * @author hoojo
 * @createDate 2018年6月19日 上午9:47:29
 * @file End2EndAndBackAgainExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.examples
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class End2EndAndBackAgainExamples {

    private static final DefaultConfiguration config = DefaultConfiguration.getConfig();
    
    private static final boolean IS_FABRIC_V10 = config.isRunningAgainstFabric10();
    private static final String ADMIN_NAME = "admin";
    private static final String USER_NAME = "user1";
    private static final String ROOT_PATH = "src/test/fixture";

    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String BAR_CHANNEL_NAME = "bar";
    
    private KeyValueFileStore store;
    private Collection<Organization> organizations;
    private File storeFile = new File("HFCSampletest.properties");

    String exampleName = "End2End BackAgain Example";

    String CHAIN_CODE_FILEPATH = "sdkintegration/gocc/sample_11";
    String CHAIN_CODE_NAME = "example_cc_go";
    String CHAIN_CODE_PATH = "github.com/example_cc";
    String CHAIN_CODE_VERSION_11 = "11";
    String CHAIN_CODE_VERSION = "1";
    TransactionRequest.Type CHAIN_CODE_LANG = TransactionRequest.Type.GO_LANG;

    ChaincodeID chaincodeID = ChaincodeID.newBuilder()
    		.setName(CHAIN_CODE_NAME)
            .setVersion(CHAIN_CODE_VERSION)
            .setPath(CHAIN_CODE_PATH).build();
    
    ChaincodeID chaincodeID_11 = ChaincodeID.newBuilder()
    		.setName(CHAIN_CODE_NAME)
            .setVersion(CHAIN_CODE_VERSION_11)
            .setPath(CHAIN_CODE_PATH).build();


    @Before
    public void checkConfig() {
    	out("\n\n\n~~~~~~~~~~~~RUNNING: %s~~~~~~~~~~~~~~~", exampleName);
    	
    	try {
    		// out("reset default config");
    		// DefaultConfiguration.resetConfig();
    		//resetConfig();
    		//configHelper.customizeConfig();
    		
    		out("Get all Organizations");
    		organizations = config.getOrganizations();
    		
    		//Set up hfca for each sample org
    		out("Set Organization Ca Client");
    		for (Organization org : organizations) {
    			String caURL = org.getCALocation();
    			org.setCAClient(HFCAClient.createNewInstance(caURL, null));
    		}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    @Test
    public void setupChaincode() {
        try {
        	out("Create KeyValue File Store");
            store = new KeyValueFileStore(storeFile);

            out("Restore Organization User");
            restoreOrgUsers(store);
            
            out("Run Chaincode");
            runChaincode(store);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   /**
    * 从持久化的数据中恢复注册用户数据，如果不存在就创建新用户
    * @author hoojo
    * @createDate 2018年6月19日 上午10:24:43
    * @param store
    */
    private void restoreOrgUsers(KeyValueFileStore store) {

        /** 从组织中获取用户 */
        for (Organization org : organizations) {
            final String orgName = org.getName();

            OrganizationUser admin = store.getMember(ADMIN_NAME, orgName);
            print("restore admin: %s", json(admin));
            org.setAdmin(admin); // The admin of this org.

            // No need to enroll or register all done in End2endIt !
            OrganizationUser user = store.getMember(USER_NAME, orgName);
            print("restore user: %s", json(user));
            org.addUser(user);  //Remember user belongs to this Org

            org.setPeerAdmin(store.getMember(orgName + "Admin", orgName));
            print("restore peer-admin: %s", json(org.getPeerAdmin()));
        }
    }
    
    private void runChaincode(final KeyValueFileStore sampleStore) throws Exception {
        out("Setup client");

    	out("Create instance of client.");
        HFClient client = HFClient.createNewInstance();

        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        Organization org = config.getOrganization("peerOrg1");
        
        out("prepare the channels");
        Channel fooChannel = prepareChannel(FOO_CHANNEL_NAME, client, org);
        
        out("Run the channels");
        runChannel(client, fooChannel, org, 0);
        
        assertFalse("fooChannel通道已关闭", fooChannel.isShutdown());
        assertTrue("fooChannel通道已初始化", fooChannel.isInitialized());
        
        fooChannel.shutdown(true); //clean up resources no longer needed.
        
        assertTrue("fooChannel通道没有关闭", fooChannel.isShutdown());
        out("\n");

        org = config.getOrganization("peerOrg2");
        
        out("prepare the channels");
        Channel barChannel = prepareChannel(BAR_CHANNEL_NAME, client, org);
        
        out("Run the channels");
        runChannel(client, barChannel, org, 100); //run a newly constructed foo channel with different b value!
        
        assertFalse("fooChannel通道已关闭", barChannel.isShutdown());
        assertTrue("fooChannel通道已初始化", barChannel.isInitialized());

        if (!config.isRunningAgainstFabric10()) { //从 v1.1 开始支持对等节点事务服务
        	out("-------------------------测试事件过滤开始-------------------------");

            // 现在测试V1.1对等事件服务的功能。
            byte[] replayChannelBytes = barChannel.serializeChannel();
            barChannel.shutdown(true);

            Channel replayChannel = client.deSerializeChannel(replayChannelBytes);

            out("doing testPeerServiceEventingReplay,0,-1,false");
            testPeerServiceEventingReplay(client, replayChannel, 0L, -1L, false);

            replayChannel = client.deSerializeChannel(replayChannelBytes);
            out("doing testPeerServiceEventingReplay,0,-1,true"); // block 0 is import to test
            testPeerServiceEventingReplay(client, replayChannel, 0L, -1L, true);

            //Now do it again starting at block 1
            replayChannel = client.deSerializeChannel(replayChannelBytes);
            out("doing testPeerServiceEventingReplay,1,-1,false");
            testPeerServiceEventingReplay(client, replayChannel, 1L, -1L, false);

            //Now do it again starting at block 2 to 3
            replayChannel = client.deSerializeChannel(replayChannelBytes);
            out("doing testPeerServiceEventingReplay,2,3,false");
            testPeerServiceEventingReplay(client, replayChannel, 2L, 3L, false);
            out("-------------------------测试事件过滤结束-------------------------");
        }

        out("That's all folks!");
    }

    private String queryAmount(HFClient client, Channel channel, final String money, ChaincodeID chaincodeID, Organization org) throws Exception {
        out("发起查询chaincode: %s 在 channel: %s 上设置参数  b : %s", chaincodeID, channel.getName(), money);
        
        // 普通查询 设置普通User
        client.setUserContext(org.getUser(USER_NAME));
        
        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setChaincodeID(chaincodeID);
        queryByChaincodeRequest.setFcn("query");
        queryByChaincodeRequest.setArgs("b".getBytes(UTF_8)); // 使用byte参数. End2EndExample 使用 Strings.

        Collection<ProposalResponse> responses;
        try {
            responses = channel.queryByChaincode(queryByChaincodeRequest);
        } catch (Exception e) {
            throw new CompletionException(e);
        }

        String payload = null;
        for (ProposalResponse response : responses) {
            if (!response.isVerified() || response.getStatus() != Status.SUCCESS) {
                throw new RuntimeException("查询失败， peer " + response.getPeer().getName() + "， status: " + response.getStatus() + ". Messages: " + response.getMessage() + ". Was verified : " + response.isVerified());
            } else {
                payload = response.getProposalResponse().getResponse().getPayload().toStringUtf8();
                print("查询来自对等点：%s ，返回金额：%s，交易金额：%s", response.getPeer().getName(), payload, money);
                checkArgument(StringUtils.equals(payload, money), "交易结果和查询结果不一致");
            }
        }
        
        return payload;
    }
    
    private Collection<ProposalResponse> installChaincode(boolean changeContext, HFClient client, Channel channel, Organization org) throws Exception {
    	out("通道：%s 安装chaincode: %s", channel.getName(), chaincodeID);
    	
    	client.setUserContext(org.getPeerAdmin());

        // 构建安装请求提议
        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
        installProposalRequest.setChaincodeSourceLocation(Paths.get(ROOT_PATH, CHAIN_CODE_FILEPATH).toFile());
        installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION_11);
        installProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
        installProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);

        if (changeContext) {
            installProposalRequest.setUserContext(org.getPeerAdmin());
        }

        ////////////////////////////
        // 只有来自同一组织的客户端才能发出安装请求
        final Collection<ProposalResponse> successful = new LinkedList<>();
        final Collection<ProposalResponse> failed = new LinkedList<>();
        
        Collection<Peer> peers = channel.getPeers();

        // 发送chaincode安装请求
        Collection<ProposalResponse> responses = client.sendInstallProposal(installProposalRequest, peers);
        print("向channel.Peers节点——发送安装chaincode请求：%s", json(installProposalRequest));
        
        for (ProposalResponse response : responses) {
    		if (response.getStatus() == Status.SUCCESS) {
    			successful.add(response);
    			print("成功安装 Txid: %s , peer: %s", response.getTransactionID(), response.getPeer().getName());
    		} else {
    			failed.add(response);
    			print("失败安装 Txid: %s , peer: %s", response.getTransactionID(), response.getPeer().getName());
    		}
    	}
    	
    	print("接收安装请求数量： %d， 成功安装并验证通过数量: %d . 失败数量: %d", peers.size(), successful.size(), failed.size());
    	if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            throw new RuntimeException("没有足够的 endorsers 安装 :" + successful.size() + "，  " + first.getMessage());
        }
    	
    	return successful;
    }
    
    private TransactionEvent upgradeChaincode(boolean changeContext, HFClient client, Channel channel, Organization org) throws Exception {
    	out("通道：%s 升级安装 chaincode: %s", channel.getName(), chaincodeID_11);
    	
    	UpgradeProposalRequest upgradeProposalRequest = client.newUpgradeProposalRequest();
        upgradeProposalRequest.setChaincodeID(chaincodeID_11);
        upgradeProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
        upgradeProposalRequest.setFcn("init");
        upgradeProposalRequest.setArgs(new String[] {});    // no arguments don't change the ledger see chaincode.

        // 设置背书策略
        ChaincodeEndorsementPolicy policy = new ChaincodeEndorsementPolicy();
        policy.fromYamlFile(new File(ROOT_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
        upgradeProposalRequest.setChaincodeEndorsementPolicy(policy);
        
        Map<String, byte[]> tmap = new HashMap<>();
        tmap.put("test", "data".getBytes());
        upgradeProposalRequest.setTransientMap(tmap);

        if (changeContext) {
            upgradeProposalRequest.setUserContext(org.getPeerAdmin());
        }

        final Collection<ProposalResponse> successful = new LinkedList<>();
        final Collection<ProposalResponse> failed = new LinkedList<>();

        // 发送安装升级chaincode请求
        Collection<ProposalResponse> responses = channel.sendUpgradeProposal(upgradeProposalRequest);
        print("向channel节点——发送安装升级chaincode请求：%s", json(upgradeProposalRequest));

        for (ProposalResponse response : responses) {
    		if (response.getStatus() == Status.SUCCESS) {
    			successful.add(response);
    			print("成功升级 Txid: %s , peer: %s", response.getTransactionID(), response.getPeer().getName());
    		} else {
    			failed.add(response);
    			print("失败升级 Txid: %s , peer: %s", response.getTransactionID(), response.getPeer().getName());
    		}
    	}
    	
    	print("接收安装请求数量： %d， 成功安装并验证通过数量: %d . 失败数量: %d", channel.getPeers().size(), successful.size(), failed.size());
    	if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            throw new RuntimeException("没有足够的 endorsers 安装 :" + successful.size() + "，  " + first.getMessage());
        }
    	
    	out("向Orderer节点——发起 执行Chaincode 升级");
        if (changeContext) {
            return channel.sendTransaction(successful, org.getPeerAdmin()).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        } else {
            return channel.sendTransaction(successful).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        }
    }
    
    int latestMount = 300;
    
    private void runChannel(HFClient client, Channel channel, Organization org, final int money) {
    	final String channelName = channel.getName();
    	out("Running Channel %s with a money %d", channelName, money);
    	print("ChaincodeID: %s", chaincodeID);

        
        try {
        	final boolean changeContext = BAR_CHANNEL_NAME.equals(channel.getName());
            //client.setUserContext(org.getUser(USER_NAME));

        	// 查询 余额
            queryAmount(client, channel, "" + (latestMount + money), chaincodeID, org);

            //没有显示的设置一下上下文User
            if (changeContext) {
                client.setUserContext(org.getUser(USER_NAME));
            }

            // v1 版本 chaincode 交易
            moveAmount(client, channel, chaincodeID, "25", changeContext ? org.getPeerAdmin() : null)
            .thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
                try {
                    waitOnFabric();
                    
                    queryAmount(client, channel, "" + (latestMount + 25 + money), chaincodeID, org);

                    // 安装 chaincode
                    installChaincode(changeContext, client, channel, org);

                    // 升级 chaincode
                    return upgradeChaincode(changeContext, client, channel, org);

                } catch (CompletionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).thenApply(transactionEvent -> {
                try {
                    waitOnFabric(10000);

                    out("Chaincode已经升级到版本： %s", CHAIN_CODE_VERSION_11);
                    
                    // 安装 升级 chaincode 需要 PeerAdmin
                    client.setUserContext(org.getPeerAdmin());

                    //检查对等体是否有新链代码，旧链代码是否消失。
                    for (Peer peer : channel.getPeers()) {
                        if (!checkInstalledChaincode(client, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION_11)) {
                            throw new AssertionError(format("在Peer %s 没有安装 chaincode:%s, path: %s, version: %s", peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION_11));
                        }

                        //should be instantiated too..
                        if (!checkInstantiatedChaincode(channel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION_11)) {
                            throw new AssertionError(format("在 Peer %s 没有实例化 chaincode:%s, path: %s, version: %s",peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION_11));
                        }

                        // 已经升级
                        if (checkInstantiatedChaincode(channel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                        	throw new AssertionError(format("在 Peer %s 存在实例化 chaincode:%s, path: %s, version: %s",peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION));
                        }
                    }

                    //client.setUserContext(org.getUser(USER_NAME));

                    ///检查我们是否仍然在分类账上获得相同的余额
                    out("查询余额：%s", money);
                    queryAmount(client, channel, "" + (latestMount + 25 + money), chaincodeID, org);

                    //运行新的chaincode 进行转账
                    CompletableFuture<TransactionEvent>  future = moveAmount(client, channel, chaincodeID_11, "50", changeContext ? org.getPeerAdmin() : null);

                    return future.get(config.getTransactionWaitTime(), TimeUnit.SECONDS); // really move 100
                } catch (CompletionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).thenApply(transactionEvent -> {
                waitOnFabric(10000);

                try {
					return queryAmount(client, channel, "" + (latestMount + 100 + 25 + money), chaincodeID_11, org);
				} catch (Exception e1) {
					e1.printStackTrace();
					return null;
				}
            }).exceptionally(e -> {
                if (e instanceof CompletionException && e.getCause() != null) {
                    e = e.getCause();
                }
                
                if (e instanceof TransactionEventException) {
                    BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                    if (te != null) {
                        e.printStackTrace(System.err);
                        fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                    }
                }

                e.printStackTrace(System.err);
                fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

                return null;
            }).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        out("Running for Channel %s done", channelName);
    }

    private CompletableFuture<TransactionEvent> moveAmount(HFClient client, Channel channel, ChaincodeID chaincodeID, String amount, User user) {
    	out("在通道：%s，发起调用Chaincode 交易业务: %s", channel.getName(), chaincodeID);
    	
        try {
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            // 构建交易提议
			TransactionProposalRequest request = client.newTransactionProposalRequest();
			request.setProposalWaitTime(config.getProposalWaitTime());
			request.setChaincodeID(chaincodeID);
			request.setFcn("move");
			request.setArgs(new byte[][] { "a".getBytes(UTF_8), "b".getBytes(UTF_8), amount.getBytes(UTF_8) });

			if (user != null) { // 使用特定用户
				request.setUserContext(user);
			}
			
            // 发起交易提议
            Collection<ProposalResponse> responses = channel.sendTransactionProposal(request);
            print("向 channel.Peers节点——发起交易“提议”请求，参数: %s", json(request));
            
            for (ProposalResponse response : responses) {
				if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
					print("交易成功 Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
					successful.add(response);
				} else {
					print("交易失败 Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
					failed.add(response);
				}
			}
            
            out("接收交易请求响应： %s ，Successful+verified: %s， Failed: %s", responses.size(), successful.size(), failed.size());
            
            if (failed.size() > 0) {
                ProposalResponse firstResponse = failed.iterator().next();
                throw new ProposalException("没有足够的背书节点调用: " + failed.size() + "， endorser error: " + firstResponse.getMessage() + ". Was verified: " + firstResponse.isVerified());
            }
            out("成功收到交易提议响应请求.");

            print("向Orderer发起Chaincode Invoke交易：%s", amount);
            if (user != null) {
                return channel.sendTransaction(successful, user);
            }
            return channel.sendTransaction(successful);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }
    
    private static boolean checkInstalledChaincode(HFClient client, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
        out("检查是否存在chaincode: %s, version: %s, peer: %s", ccName, ccVersion, peer.getName());
        
        List<ChaincodeInfo> list = client.queryInstalledChaincodes(peer);
        
        boolean found = false;
        for (ChaincodeInfo chaincodeInfo : list) {
        	print("已安装chaincode：%s", json(chaincodeInfo));
        	
            if (ccPath != null) {
                found = ccName.equals(chaincodeInfo.getName()) && ccPath.equals(chaincodeInfo.getPath()) && ccVersion.equals(chaincodeInfo.getVersion());
                if (found) {
                    break;
                }
            }

            found = ccName.equals(chaincodeInfo.getName()) && ccVersion.equals(chaincodeInfo.getVersion());
            if (found) {
                break;
            }
        }

        return found;
    }

    private static boolean checkInstantiatedChaincode(Channel channel, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
        out("在通道：%s 检查是否实例化 chaincode: %s, version: %s, peer: %s", channel.getName(), ccName, ccVersion, peer.getName());
        
        List<ChaincodeInfo> chaincodeList = channel.queryInstantiatedChaincodes(peer);

        boolean found = false;
        for (ChaincodeInfo chaincodeInfo : chaincodeList) {
        	print("已实例化 chaincode：%s", json(chaincodeInfo));
        	
            if (ccPath != null) {
                found = ccName.equals(chaincodeInfo.getName()) && ccPath.equals(chaincodeInfo.getPath()) && ccVersion.equals(chaincodeInfo.getVersion());
                if (found) {
                    break;
                }
            }

            found = ccName.equals(chaincodeInfo.getName()) && ccVersion.equals(chaincodeInfo.getVersion());
            if (found) {
                break;
            }
        }

        return found;
    }

    
    private Channel createChannel(String channelName, HFClient client, Organization org) throws Exception {
    	print("创建新的通道：%s", channelName);
    	
    	Channel channel = client.newChannel(channelName);
        
        return channel;
    }
    
    private void createOrderer(Channel channel, HFClient client, Organization org) throws Exception {
    	print("在通道：%s 添加Orderer节点", channel.getName());
    	
        for (String ordererName : org.getOrdererNames()) {
        	print("Channel add Orderer: %s", ordererName);
            channel.addOrderer(client.newOrderer(ordererName, org.getOrdererLocation(ordererName), config.getOrdererProperties(ordererName)));
        }
    }
    
    private void createPeer(Channel channel, HFClient client, Organization org) throws Exception {
    	print("在通道：%s 添加Peer节点", channel.getName());
    	
    	boolean everyOther = false;
        for (String peerName : org.getPeerNames()) {
            String grpcURL = org.getPeerLocation(peerName);
            print("创建对等节点:%s，URL：%s", peerName, grpcURL);
            
            Properties peerProperties = config.getPeerProperties(peerName);
            
            // 创建Peer节点
            Peer peer = client.newPeer(peerName, grpcURL, peerProperties);
            
            // 设置Peer选项
            PeerOptions peerOptions = null;
            if (everyOther) {
            	peerOptions = createPeerOptions().registerEventsForBlocks();
            } else {
            	peerOptions = createPeerOptions().registerEventsForFilteredBlocks();
            }

            // 向通道 添加Peer节点
            if (IS_FABRIC_V10) {
            	channel.addPeer(peer, createPeerOptions().setPeerRoles(PeerRole.NO_EVENT_SOURCE));
            } else {
            	channel.addPeer(peer, peerOptions);
            }

            everyOther = !everyOther;
        }
    }
    
    private void createEventHub(Channel channel, HFClient client, Organization org) throws Exception {
    	print("在通道：%s 添加事务总线", channel.getName());
    	
    	//为了混合起来测试。 对于v1.1，只使用foo 通道的对等节点事件服务
        if (IS_FABRIC_V10) {
            assertTrue("存在带EVENT_SOURCE事件源的对等节点", channel.getPeers(EnumSet.of(PeerRole.EVENT_SOURCE)).isEmpty());
            assertEquals("不具有2个NO_EVENT_SOURCE的事件对等节点", 2, channel.getPeers(PeerRole.NO_EVENT_SOURCE).size());
            
            for (String eventHubName : org.getEventHubNames()) {
            	String grpcURL = org.getEventHubLocation(eventHubName);
            	print("添加事件监听：%s，URL：%s", eventHubName, grpcURL);
            	
                EventHub eventHub = client.newEventHub(eventHubName, grpcURL, config.getEventHubProperties(eventHubName));
                channel.addEventHub(eventHub);
            }
        } else {
            //对等节点应该拥有所有事件角色

            assertEquals("2个节点拥有EVENT_SOURCE事件角色", 2, channel.getPeers(EnumSet.of(PeerRole.EVENT_SOURCE)).size());
            assertEquals("2个节点拥有CHAINCODE_QUERY & LEDGER_QUERY事件角色", 2, channel.getPeers(EnumSet.of(PeerRole.CHAINCODE_QUERY, PeerRole.LEDGER_QUERY)).size());
            assertEquals("2个节点拥有ALL事件角色", 2, channel.getPeers(PeerRole.ALL).size());  //really same as newChannel.getPeers()
        }

        assertEquals(IS_FABRIC_V10 ? org.getEventHubNames().size() : 0, channel.getEventHubs().size());
    }
    
    private Channel restoreChannel(String channelName, HFClient client) throws Exception {
    	print("恢复通道: %s", channelName);
    	
    	// 从缓存获取通道，如果不存在就构建通道对象
    	Channel channel = store.getChannel(client, channelName);
        if (!IS_FABRIC_V10) {
            // Make sure there is one of each type peer at the very least. see End2end for how peers were constructed.
            assertFalse("通道不存在EVENT_SOURCE事件的Peer节点", channel.getPeers(EnumSet.of(PeerRole.EVENT_SOURCE)).isEmpty());
            assertFalse("通道不存在NO_EVENT_SOURCE事件的Peer节点", channel.getPeers(PeerRole.NO_EVENT_SOURCE).isEmpty());

        }
        assertEquals("事件总线长度不为2", 2, channel.getEventHubs().size());
        
        print("成功恢复通道：%s", channelName);
        
        return channel;
    }
    
    private Channel restoreOrCreateChannel(String channelName, HFClient client, Organization org) throws Exception {
    	print("-------开始恢复或创建通道：%s-------", channelName);
    	
    	Channel channel = null;
    	if (BAR_CHANNEL_NAME.equals(channelName)) { // bar channel 将从 End2endExample 的store中进行恢复.
    		
    		channel = restoreChannel(channelName, client);
    		
        } else {
        	/** 创建通道 */
        	channel = createChannel(channelName, client, org);
        	
        	createOrderer(channel, client, org);

            /** 创建Orderer */
            createPeer(channel, client, org);

            createEventHub(channel, client, org);
        }
    	
    	print("-------返回恢复或创建的通道： %s-------", channelName);
    	return channel;
    }
    
	/**
	 * 检查“序列化/反序列化”通道。
	 * @author hoojo
	 * @createDate 2018年6月19日 下午3:07:13
	 * @throws Exception
	 */
	private Channel checkChannelSerialize(String channelName, Channel channel, HFClient client) throws Exception {
		out("检查通道可否序列化：%s", channel.getName());
		
		//Just some sanity check tests
        assertTrue("通道一致且相同", channel == client.getChannel(channelName));
        assertTrue("客户端一致且相同", client == TestUtils.getField(channel, "client"));
        assertEquals("通道名称相同", channelName, channel.getName());
        assertEquals("2个Peer节点", 2, channel.getPeers().size());
        assertEquals("1个Orderer节点", 1, channel.getOrderers().size());
        assertFalse("通道已关闭", channel.isShutdown());
        assertFalse("通道未初始化", channel.isInitialized());
        
        // 序列化并关闭通道
        byte[] serializedChannelBytes = channel.serializeChannel();
        
        channel.shutdown(true);
        channel = client.deSerializeChannel(serializedChannelBytes);

        assertEquals("2个Peer节点", 2, channel.getPeers().size());
        assertEquals("1个Orderer节点", 1, channel.getOrderers().size());

        assertNotNull("HF客户端存在通道", client.getChannel(channelName));
        assertTrue("通道一致且相同", channel == client.getChannel(channelName));
        assertFalse("通道已关闭", channel.isShutdown());
        assertFalse("通道未初始化", channel.isInitialized());
        
        assertEquals("客户端上下文用户是： " + USER_NAME, USER_NAME, client.getUserContext().getName());
        channel.initialize();
        assertTrue(channel.isInitialized());
        assertFalse(channel.isShutdown());
        
        return channel;
	}
	
	private void checkChannel(String channelName, Channel channel, HFClient client) throws Exception {
		out("开始使用反序列化通道进行测试");
		
		// 查找指定通道是否在Peer节点中存在
        for (Peer peer : channel.getPeers()) {
            Set<String> channels = client.queryChannels(peer);
            print("通过对等节点：%s 找到通道：%s", peer.getName(), channels);
            if (!channels.contains(channelName)) {
                throw new AssertionError(format("对等节点  %s 中没有通道 ", peer.getName(), channelName));
            }
        }

        // 获取通道配置数据
        final byte[] channelConfigurationBytes = channel.getChannelConfigurationBytes();
        
        // 转换为 channel.tx config 配置
        Configtx.Config channelConfig = Configtx.Config.parseFrom(channelConfigurationBytes);
        assertNotNull("获取的通道配置不为空", channelConfig);

        Configtx.ConfigGroup channelGroup = channelConfig.getChannelGroup();
        assertNotNull("ConfigGroup 不为空", channelGroup);

        Map<String, Configtx.ConfigGroup> groupsMap = channelGroup.getGroupsMap();
        print("GroupsMap: %s", groupsMap);
        
        assertNotNull("Orderer 配置不为空", groupsMap.get("Orderer"));
        assertNotNull("Application 配置不为空", groupsMap.get("Application"));
	}
    
	private void checkChaincode(Channel channel, HFClient client, Organization org) throws Exception {
		out("检查通道中的Chaincode是否安装或实例化");
		
		// 设置对等节点用户上下文
        client.setUserContext(org.getPeerAdmin());

        for (Peer peer : channel.getPeers()) {
            if (!checkInstalledChaincode(client, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                throw new AssertionError(format("Peer节点 %s 没有安装 chaincode: %s, path: %s, version: %s", peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
            }

            if (!checkInstantiatedChaincode(channel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                throw new AssertionError(format("Peer节点 %s 没有实例化 chaincode: %s, path: %s, version: %s", peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
            }
        }
	}
	
    private Channel prepareChannel(String channelName, HFClient client, Organization org) throws Exception {
        out("开始准备通道：%s", channelName);

        client.setUserContext(org.getUser(USER_NAME));

        /** 恢复或创建通道 */
        Channel channel = restoreOrCreateChannel(channelName, client, org);
        
        channel = checkChannelSerialize(channelName, channel, client);
        
        checkChannel(channelName, channel, client);

        checkChaincode(channel, client, org);

        client.setUserContext(org.getUser(USER_NAME));
        assertTrue("通道已经初始化，不可使用", channel.isInitialized());
        assertFalse("通道没有关闭，不可使用", channel.isShutdown());

        out("成功完成准备通道：%s", channelName);
        return channel;
    }
    

    /**
     * 此代码测试新对等事件服务的重播功能。
     * 不是默认启动事件对等体来检索最新的块，而是设置它从start参数开始检索。 还检查块和filterblock重播。
     * 取决于end2end和end2endandackagain的完全运行有区块需要处理。
     */
    @SuppressWarnings("deprecation")
	private void testPeerServiceEventingReplay(HFClient client, Channel channel, final long start, final long stop, final boolean useFilteredBlocks) throws InvalidArgumentException {
        if (config.isRunningAgainstFabric10()) {
            return; // not supported for v1.0
        }

        assertFalse("通道未初始化", channel.isInitialized()); //not yet initialized
        assertFalse("通道已关闭", channel.isShutdown()); // not yet shutdown.

        // 删除所有对等节点只需要一个分类账对等节点和一个事件对等节点
        List<Peer> peers = new ArrayList<>(channel.getPeers());
        for (Peer peer : peers) {
        	print("删除通道上：%s 对等节点：%s", channel.getName(), peer.getName());
        	
            channel.removePeer(peer);
        }
        assertTrue("至少有一个对等节点", peers.size() > 1); //need at least two
        
        final Peer eventingPeer = peers.remove(0); // 事件节点
        Peer ledgerPeer = peers.remove(0); // 账本节点

        assertTrue("通道上还存在Peer节点", channel.getPeers().isEmpty()); // no more peers.
        assertTrue("通道上存在 CHAINCODE_QUERY & ENDORSING_PEER 角色节点", channel.getPeers(EnumSet.of(PeerRole.CHAINCODE_QUERY, PeerRole.ENDORSING_PEER)).isEmpty()); // just checking :)
        assertTrue("通道上存在 LEDGER_QUERY 角色节点", channel.getPeers(EnumSet.of(PeerRole.LEDGER_QUERY)).isEmpty()); // just checking

        assertNotNull("客户端上没有通道：" + channel.getName(), client.getChannel(channel.getName())); // should be known by client.

        // 设置 事件角色类型 选项
        final PeerOptions eventingPeerOptions = createPeerOptions().setPeerRoles(EnumSet.of(PeerRole.EVENT_SOURCE));
        if (useFilteredBlocks) {
            eventingPeerOptions.registerEventsForFilteredBlocks(); // 注册一个 过滤区块 的事件模型
        }

        // 支持事件过滤 对等节点
        if (-1L == stop) { //区块链的高度
            channel.addPeer(eventingPeer, eventingPeerOptions.startEvents(start)); // 事件对等节点 开始从块0获取块
        } else {
            channel.addPeer(eventingPeer, eventingPeerOptions.startEvents(start).stopEvents(stop)); // 从0开始获取区块，从stop结束
        }
        
        //add a ledger peer 支持账本数据查询角色对等节点
        channel.addPeer(ledgerPeer, createPeerOptions().setPeerRoles(EnumSet.of(PeerRole.LEDGER_QUERY)));

        
        CompletableFuture<Long> done = new CompletableFuture<>(); // future to set when done.
        // some variable used by the block listener being set up.
        final AtomicLong bcount = new AtomicLong(0);
        final AtomicLong stopValue = new AtomicLong(stop == -1L ? Long.MAX_VALUE : stop);
        
        final Channel finalChannel = channel;
        final Map<Long, BlockEvent> blockEvents = Collections.synchronizedMap(new HashMap<>(100));

        final String blockListenerHandle = channel.registerBlockListener(blockEvent -> { // register a block listener

            try {
                final long blockNumber = blockEvent.getBlockNumber();
                
                BlockEvent seen = blockEvents.put(blockNumber, blockEvent);
                assertNull(format("Block number %d seen twice", blockNumber), seen);

                assertTrue(format("错误的块号： %d。 预期的过滤: %b，最终过滤： %b",
                        blockNumber, useFilteredBlocks, blockEvent.isFiltered()),
                        useFilteredBlocks ? blockEvent.isFiltered() : !blockEvent.isFiltered());
                
                final long count = bcount.getAndIncrement(); //count starts with 0 not 1 !
                //out("Block count: %d, block number: %d  received from peer: %s", count, blockNumber, blockEvent.getPeer().getName());

                if (count == 0 && stop == -1L) {
                    final BlockchainInfo blockchainInfo = finalChannel.queryBlockchainInfo();

                    long lh = blockchainInfo.getHeight();
                    stopValue.set(lh - 1L);  // blocks 0L 9L are on chain height 10 .. stop on 9
                    //  out("height: %d", lh);
                    if (bcount.get() + start > stopValue.longValue()) { // test with latest count.
                        done.complete(bcount.get()); // report back latest count.
                    }
                } else {
                    if (bcount.longValue() + start > stopValue.longValue()) {
                        done.complete(count);
                    }
                }
            } catch (AssertionError | Exception e) {
                e.printStackTrace();
                done.completeExceptionally(e);
            }
        });

        try {
            channel.initialize(); // start it all up.
            
            done.get(30, TimeUnit.SECONDS); // give a timeout here.
            Thread.sleep(1000); // sleep a little to see if more blocks trickle in .. they should not
            
            // 取消区块事件注册
            channel.unregisterBlockListener(blockListenerHandle);

            final long expectNumber = stopValue.longValue() - start + 1L; // Start 2 and stop is 3  expect 2
            assertEquals(format("没有得到预计的值： %d ，但得到了%d个块事件。 开始：%d，结束：%d，高度：%d",
                    expectNumber, blockEvents.size(), start, stop, stopValue.longValue()), expectNumber, blockEvents.size());

            for (long i = stopValue.longValue(); i >= start; i--) { //make sure all are there.
                final BlockEvent blockEvent = blockEvents.get(i);
                
                String dataHash = null;
                if (blockEvent.getDataHash() != null) {
                	dataHash = Hex.encodeHexString(blockEvent.getDataHash());
                }
                String prevHash = null;
                if (blockEvent.getPreviousHash() != null) {
                	prevHash = Hex.encodeHexString(blockEvent.getPreviousHash());
                }
                
                print("BlockNumber: %s, dataHash: %s, prevHash: %s", blockEvent.getBlockNumber(), dataHash, prevHash);
                print("BlockNumber: %s, EnvelopeCount: %s, TransactionCount: %s", blockEvent.getBlockNumber(), blockEvent.getEnvelopeCount(), blockEvent.getTransactionCount());
                
                assertNotNull(format("缺少区块事件 block number： %d. Start= %d", i, start), blockEvent);
            }

            int transactionEventCounts = 0;
            int chaincodeEventsCounts = 0;

            for (long i = stopValue.longValue(); i >= start; i--) {

                final BlockEvent blockEvent = blockEvents.get(i);
                out("filter block： %b, start: %d, stop: %d, i: %d, block %d", useFilteredBlocks, start, stopValue.longValue(), i, blockEvent.getBlockNumber());
                
                String dataHash = null;
                if (blockEvent.getDataHash() != null) {
                	dataHash = Hex.encodeHexString(blockEvent.getDataHash());
                }
                String prevHash = null;
                if (blockEvent.getPreviousHash() != null) {
                	prevHash = Hex.encodeHexString(blockEvent.getPreviousHash());
                }
                
                print("BlockNumber: %s, dataHash: %s, prevHash: %s", blockEvent.getBlockNumber(), dataHash, prevHash);
                print("BlockNumber: %s, EnvelopeCount: %s, TransactionCount: %s", blockEvent.getBlockNumber(), blockEvent.getEnvelopeCount(), blockEvent.getTransactionCount());

                assertEquals("过滤不一致", useFilteredBlocks, blockEvent.isFiltered()); // check again
                if (useFilteredBlocks) {
                    assertNull("有区块事件的区块", blockEvent.getBlock()); // should not have raw block event.
                    assertNotNull("没有过滤到区块", blockEvent.getFilteredBlock()); // should have raw filtered block.
                } else {
                    assertNotNull("没有区块事件的区块", blockEvent.getBlock()); // should not have raw block event.
                    assertNull("有过滤到区块", blockEvent.getFilteredBlock()); // should have raw filtered block.
                }

                assertEquals("通道名称不一致", channel.getName(), blockEvent.getChannelId());

                for (BlockInfo.EnvelopeInfo envelopeInfo : blockEvent.getEnvelopeInfos()) {
                	
                	print("  tx number: %d, channel id: %s", i, envelopeInfo.getChannelId());
                    print("  tx number: %d, epoch: %d", i, envelopeInfo.getEpoch());
                    print("  tx number: %d, timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
                    print("  tx number: %d, type id: %s", i, "" + envelopeInfo.getType());
                    print("  tx number: %d, valid code: %s", i, "" + envelopeInfo.getValidationCode());
                    if (envelopeInfo.getNonce() != null) {
                    	print("  tx number: %d, nonce : %s", i, "" + Hex.encodeHexString(envelopeInfo.getNonce()));
                    }
                    if (envelopeInfo.getCreator() != null) {
                    	print("  tx number: %d, submitter mspid: %s,  certificate: %s", i, envelopeInfo.getCreator().getMspid(), envelopeInfo.getCreator().getId());
                    }
                    
                    if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) { // 交易类型

                        BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;
                        assertTrue("交易信封信息无效", envelopeInfo.isValid()); // only have valid blocks.
                        assertEquals("交易码不为0", envelopeInfo.getValidationCode(), 0);
                        
                        ++transactionEventCounts;
                        for (BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo ta : transactionEnvelopeInfo.getTransactionActionInfos()) {
                        	print("ArgsCount: %d, ProposalStatus: %s, Message: %s, ResponseStatus: %s", ta.getChaincodeInputArgsCount(), ta.getProposalResponseStatus(), ta.getResponseMessage(), ta.getResponseStatus());
                        	
                            //    out("\nTA:", ta + "\n\n");
                            ChaincodeEvent event = ta.getEvent();
                            if (event != null) {
                            	print("ccId: %s, event: %s, txId: %s, payload: %s", event.getChaincodeId(), event.getEventName(), event.getTxId(), new String(event.getPayload()));
                                assertNotNull("交易id为空", event.getChaincodeId());
                                assertNotNull("交易事件名称为空", event.getEventName());
                                chaincodeEventsCounts++;
                            }
                        }
                    } else {
                        assertEquals("只有非交易区块，blockNumber=0.", blockEvent.getBlockNumber(), 0);
                    }
                }
            }

            assertTrue(transactionEventCounts > 0);

            if (expectNumber > 4) { // this should be enough blocks with CC events.
                assertTrue(chaincodeEventsCounts > 0);
            }

            channel.shutdown(true); //all done.
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();

        System.out.println("\n~~~~~~~~~~~~~~~~~~~~" + format(format, args) + "~~~~~~~~~~~~~~~~~~~~\n");
        System.err.flush();
        System.out.flush();
    }
    
    private static void print(String format, Object... args) {
		System.err.flush();
		System.out.flush();
		System.out.println(format(format, args));
		System.err.flush();
		System.out.flush();
	}
    
    private static String json(Object o) {
		if (o != null)
			return new Gson().toJson(o);
		
		return "NULL";
	}
    
    private void waitOnFabric() {
        waitOnFabric(0);
    }

    ///// NO OP ... leave in case it's needed.
    private void waitOnFabric(int additional) {
    	/*try {
			Thread.sleep(additional * 100L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
    }
}
