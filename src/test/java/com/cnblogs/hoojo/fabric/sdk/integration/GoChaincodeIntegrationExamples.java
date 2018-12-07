package com.cnblogs.hoojo.fabric.sdk.integration;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockInfo.EnvelopeInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Channel.PeerOptions;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.Peer.PeerRole;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.junit.Before;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.core.ChaincodeManager;
import com.cnblogs.hoojo.fabric.sdk.core.ChannelManager;
import com.cnblogs.hoojo.fabric.sdk.core.TransactionManager;
import com.cnblogs.hoojo.fabric.sdk.core.UserManager;
import com.cnblogs.hoojo.fabric.sdk.entity.InstallEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.InstantiateUpgradeEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.SendTransactionEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.TransactionEntity;
import com.cnblogs.hoojo.fabric.sdk.event.BaseChaincodeEvent;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.google.common.collect.Maps;

/**
 * <b>function:</b> Go Chaincode Integration Example
 * 
 * @author hoojo
 * @createDate 2018年6月26日 上午9:54:31
 * @file GoChaincodeIntegrationExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.integration
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class GoChaincodeIntegrationExamples {

	private static final File storeFile = new File("HFCSampletest.properties");

	private static final String channelName = "foo"; // foo bar
	protected static final String orgName = "peerOrg1";
	
	private static final String ADMIN_NAME = "admin";
	private static final String ADMIN_SECRET = "adminpw";
	protected static final String USER_NAME = "user1";
	
	protected static String CHAIN_CODE_NAME = "example_cc_go";
	protected static String CHAIN_CODE_PATH = "github.com/example_cc";
	protected static final String CHAIN_CODE_VERSION = "1";
	protected static final String CHAIN_CODE_VERSION_11 = "11";
	protected static Type CHAIN_CODE_LANG = Type.GO_LANG;
	
	private static final String EXPECTED_EVENT_NAME = "event";
	private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);

	protected static final DefaultConfiguration config = DefaultConfiguration.getConfig();
	protected static ChaincodeID id = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setPath(CHAIN_CODE_PATH).setVersion(CHAIN_CODE_VERSION).build();
	protected static ChaincodeID id_11 = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setPath(CHAIN_CODE_PATH).setVersion(CHAIN_CODE_VERSION_11).build();

	private KeyValueFileStore store;
	protected HFClient client;
	
	protected UserManager userManager;
	protected ChannelManager channelManager;
	protected ChaincodeManager chaincodeManager;
	protected TransactionManager transactionManager;
	
	@Before
	public void checkConfig() throws Exception {
		
		client = HFClient.createNewInstance();
		client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
		
		store = new KeyValueFileStore(storeFile);

		userManager = new UserManager(config, store);
		channelManager = new ChannelManager(config, store, client);
		transactionManager = new TransactionManager(config, client);
		chaincodeManager = new ChaincodeManager(config, client);
	}
	
	@Test
	public void testUser() {
		try {
			userManager.initialize(ADMIN_NAME, ADMIN_SECRET, USER_NAME);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testChannel() {
		
		try {
			userManager.initialize(ADMIN_NAME, ADMIN_SECRET, USER_NAME);
			
			Organization org = config.getOrganization(orgName);
			channelManager.initialize(channelName, org);
			
			org = config.getOrganization("peerOrg2");
			channelManager.initialize("bar", org);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testInstallChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			InstallEntity chaincode = new InstallEntity(id, CHAIN_CODE_LANG);
			chaincode.setChaincodeSourceFile(Paths.get(config.getChaincodePath()).toFile());
			
			client.setUserContext(org.getPeerAdmin());
			if (chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				System.out.println(String.format("通道  %s 已安装chaincode 无需重复安装：%s ", channel.getName(), chaincode.getChaincodeId()));
				return;
			}
			
			chaincodeManager.installChaincode(channel, chaincode);

			if (!chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 没有安装");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testInstantiateChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			InstantiateUpgradeEntity chaincode = new InstantiateUpgradeEntity(id, CHAIN_CODE_LANG);
			chaincode.setEndorsementPolicy(chaincodeManager.getChaincodeEndorsementPolicy());
			chaincode.setFunc("init");
			chaincode.setArgs(new String[] { "a", "500", "b", "200" });
			chaincode.setSpecificPeers(true);
			
			if (!chaincodeManager.checkInstallChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 没有安装");
			} 
			if (chaincodeManager.checkInstantiatedChaincode(channel, chaincode.getChaincodeId())) {
				throw new AssertionError("chaincode 1 已经实例化");
			}
			
			client.setUserContext(org.getPeerAdmin());
			Collection<ProposalResponse> responses = chaincodeManager.instantiateChaincode(channel, chaincode);
			
			SendTransactionEntity transaction = new SendTransactionEntity();
			transaction.setUser(org.getPeerAdmin());
			
			CompletableFuture<TransactionEvent> future = chaincodeManager.sendTransaction(responses, transaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
				checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
				// 必须有签名
				checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
				// 必须有交易区块事件发生
				BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
				
				try {
					System.out.println("成功实例化Chaincode，本次实例化交易ID：" +  transactionEvent.getTransactionID());
					checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");

					// 检查
					if (!chaincodeManager.checkInstantiatedChaincode(channel, chaincode.getChaincodeId())) {
						throw new AssertionError("chaincode 1 没有实例化");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				
				return "success";
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
			
			System.out.println("返回结果：" + result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testQueryChaincode() {
		
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			client.setUserContext(org.getUser(USER_NAME));

			TransactionEntity transaction = new TransactionEntity(id_11, CHAIN_CODE_LANG);
			transaction.setArgs(new String[] { "a" });
			transaction.setFunc("query");
			
			String account = transactionManager.queryChaincode(channel, transaction);
			System.out.println("account a: " + account);
			

			transaction.setArgs(new String[] { "b" });
			transaction.setFunc("query");
			
			account = transactionManager.queryChaincode(channel, transaction);
			System.out.println("account b: " + account);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testInvokeChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			//Vector<BaseChaincodeEvent> chaincodeEvents = new Vector<>();
			//String handler = bindChaincodeEvent(chaincodeEvents, channel);
			
			TransactionEntity transaction = new TransactionEntity(id, CHAIN_CODE_LANG);
			transaction.setArgs(new String[] { "a", "b", "5" });
			transaction.setFunc("move");
			
			Map<String, byte[]> transientMap = Maps.newHashMap();
			transientMap.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);
			transaction.setTransientMap(transientMap);
			
			client.setUserContext(org.getUser(USER_NAME));
			
			Collection<ProposalResponse> responses = transactionManager.invokeChaincode(channel, transaction);
			
			SendTransactionEntity sendTransaction = new SendTransactionEntity();
			sendTransaction.setUser(org.getUser(USER_NAME));
			
			CompletableFuture<TransactionEvent> future = transactionManager.sendTransaction(responses, sendTransaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
	    		checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
	    		// 必须有签名
	    		checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
	    		// 必须有交易区块事件发生
	    		BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
	    		
	    		try {
	    			System.out.println("成功交易，本次实例化交易ID：" +  transactionEvent.getTransactionID());
	    			checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");
	    			
	    			client.setUserContext(org.getUser(USER_NAME));

		    		transaction.setArgs(new String[] { "a" });
		    		transaction.setFunc("query");
					String account = transactionManager.queryChaincode(channel, transaction);
					System.out.println("account a: " + account);
					

					transaction.setArgs(new String[] { "b" });
					transaction.setFunc("query");
					account = transactionManager.queryChaincode(channel, transaction);
					System.out.println("account b: " + account);
					
				} catch (Exception e) {
		            throw new RuntimeException(e);
				}
	    		
				return "success";
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
			
			System.out.println(result);
			
			//checkChaincodeEvents(channel, handler, chaincodeEvents);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testUpgradeChaincode() {
		try {
			Channel channel = getChannel();
			Organization org = config.getOrganization(orgName);
			
			if (!chaincodeManager.checkChaincode(channel, id, org)) {
				throw new AssertionError("chaincode 1 没有安装和实例化");
			}
			
			client.setUserContext(org.getPeerAdmin()); // foo
			// client.setUserContext(org.getUser(USER_NAME)); // bar
			
			if (chaincodeManager.checkInstallChaincode(channel, id_11)) {
				throw new AssertionError("chaincode 11 已经安装，无需重复安装");
			} 

			// 安装版本 11
			InstallEntity chaincode_11 = new InstallEntity(id, CHAIN_CODE_LANG, CHAIN_CODE_VERSION_11);
			chaincodeManager.installChaincode(channel, chaincode_11, Paths.get(config.getCommonConfigRootPath(), "gocc/sample_11").toFile());
			
			if (!chaincodeManager.checkInstallChaincode(channel, id_11)) {
				throw new AssertionError("chaincode 11 没有安装");
			}
			
			InstantiateUpgradeEntity chaincode = new InstantiateUpgradeEntity(id_11, CHAIN_CODE_LANG);
			chaincode.setEndorsementPolicy(chaincodeManager.getChaincodeEndorsementPolicy());
			chaincode.setFunc("init");
			chaincode.setArgs(new String[] { });
			//chaincode.setSpecificPeers(false);
			chaincode.setSpecificPeers(true);
			chaincode.setUser(org.getPeerAdmin());
			
			Collection<ProposalResponse> responses = chaincodeManager.upgradeChaincode(channel, chaincode);
			
			SendTransactionEntity transaction = new SendTransactionEntity();
			transaction.setUser(org.getPeerAdmin());
			
			CompletableFuture<TransactionEvent> future = chaincodeManager.sendTransaction(responses, transaction, channel);
			Object result = future.thenApply((BlockEvent.TransactionEvent transactionEvent) -> {
				
				// 必须是有效交易事件
				checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
				// 必须有签名
				checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
				// 必须有交易区块事件发生
				BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
				
				try {
					System.out.println("成功实例化Chaincode，本次实例化交易ID：" +  transactionEvent.getTransactionID());
					checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");
					
					// 检查
					if (!chaincodeManager.checkChaincode(channel, id_11, org)) {
						throw new AssertionError("chaincode 11 没有安装或初始化");
					}
					
					if (chaincodeManager.checkInstantiatedChaincode(channel, id)) {
						//throw new AssertionError("chaincode 1  被初始化");
						System.out.println("chaincode 1  被初始化");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				
				return "success";
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
			
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testQuery() {
		
		try {
			Channel channel = getChannel();
			
			BlockchainInfo channelInfo = channel.queryBlockchainInfo();

            // 从区块链最新的地方开始往旧的区块遍历循环
            for (long current = channelInfo.getHeight() - 1; current > -1; --current) {
                BlockInfo blockInfo = channel.queryBlockByNumber(current);
                
                final long blockNumber = blockInfo.getBlockNumber();

                print("block： %d, data hash: %s", blockNumber, Hex.encodeHexString(blockInfo.getDataHash()));
                print("block: %d, previous hash: %s", blockNumber, Hex.encodeHexString(blockInfo.getPreviousHash()));
                print("block: %d, calculated block hash: %s", blockNumber, Hex.encodeHexString(SDKUtils.calculateBlockHash(client, blockNumber, blockInfo.getPreviousHash(), blockInfo.getDataHash())));

                // 此区块中的交易数量
                final int envelopeCount = blockInfo.getEnvelopeCount();
                checkArgument(envelopeCount > 0, "交易数量小于等于0");
                
                eachCheckEnvelopes(blockInfo, blockNumber);
                
                print("block: %d, envelope count: %d", blockNumber, envelopeCount);
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testFilterEvent() {
		try {
			Channel channel = getChannel();
			
			final Channel channel2 = channel;
			//Iterator<Peer> iter = channel.getPeers().iterator();
			//Peer eventingPeer = iter.next();
			//Peer ledgerPeer = iter.next();
			
			// 删除所有对等节点只需要一个分类账对等节点和一个事件对等节点
	        List<Peer> peers = new ArrayList<>(channel.getPeers());
	        for (Peer peer : peers) {
	        	print("删除通道上：%s 对等节点：%s", channel.getName(), peer.getName());
	            channel.removePeer(peer);
	        }
	        assertTrue("至少有一个对等节点", peers.size() > 1); //need at least two
	        
	        final Peer eventingPeer = peers.remove(0); // 事件节点
	        Peer ledgerPeer = peers.remove(0); // 账本节点
			
			int start = 0;
			int stop = -1;
			
			// 设置 事件角色类型 选项
	        final PeerOptions eventingPeerOptions = createPeerOptions().setPeerRoles(EnumSet.of(PeerRole.EVENT_SOURCE));
	        eventingPeerOptions.registerEventsForFilteredBlocks(); // 注册一个 过滤区块 的事件模型

	        // 支持事件过滤 对等节点
	        if (-1L == stop) { //区块链的高度
	            channel.addPeer(eventingPeer, eventingPeerOptions.startEvents(start)); // 事件对等节点 开始从块0获取块
	        } else {
	            channel.addPeer(eventingPeer, eventingPeerOptions.startEvents(start).stopEvents(stop)); // 从0开始获取区块，从stop结束
	        }
	        
	        //add a ledger peer 支持账本数据查询角色对等节点
	        channel.addPeer(ledgerPeer, createPeerOptions().setPeerRoles(EnumSet.of(PeerRole.LEDGER_QUERY)));
			
	        final String blockListenerHandle = channel.registerBlockListener(blockEvent -> { // register a block listener
	            try {
	                final long blockNumber = blockEvent.getBlockNumber();
	                
	                BlockInfo block = channel2.queryBlockByNumber(blockNumber);
	                
	                print("block： %d, data hash: %s", blockNumber, Hex.encodeHexString(block.getDataHash()));
	                print("block: %d, previous hash: %s", blockNumber, Hex.encodeHexString(block.getPreviousHash()));
	                print("block: %d, calculated block hash: %s", blockNumber, Hex.encodeHexString(SDKUtils.calculateBlockHash(client, blockNumber, block.getPreviousHash(), block.getDataHash())));

	                
	                eachCheckEnvelopes(block, blockNumber);
	            } catch (AssertionError | Exception e) {
	                e.printStackTrace();
	            }
	        });
	        
	        Thread.sleep(1000); // sleep a little to see if more blocks trickle in .. they should not
            
	        channel.initialize(); 
	        
            // 取消区块事件注册
            channel.unregisterBlockListener(blockListenerHandle);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("deprecation")
	private void eachCheckEnvelopes(BlockInfo blockInfo, long blockNumber) throws Exception {
		print("遍历区块链 Envelope Infos信息 ……");
		
		int i = 0;
        int transactionCount = 0;
        for (EnvelopeInfo envelopeInfo : blockInfo.getEnvelopeInfos()) {
            ++i;

            print("  tx number: %d", i);
            // 交易类型
            if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) {
            	++transactionCount;
            	print("  txID: %s", envelopeInfo.getTransactionID());
            }
            final String channelId = envelopeInfo.getChannelId();
            assertTrue("foo".equals(channelId) || "bar".equals(channelId));

            print("  tx number: %d, channel id: %s", i, channelId);
            print("  tx number: %d, epoch: %d", i, envelopeInfo.getEpoch());
            print("  tx number: %d, timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
            print("  tx number: %d, type id: %s", i, "" + envelopeInfo.getType());
            print("  tx number: %d, valid code: %s", i, "" + envelopeInfo.getValidationCode());
            print("  tx number: %d, nonce : %s", i, "" + Hex.encodeHexString(envelopeInfo.getNonce()));
            print("  tx number: %d, submitter mspid: %s,  certificate: %s", i, envelopeInfo.getCreator().getMspid(), envelopeInfo.getCreator());


            checkArgument(transactionCount == blockInfo.getTransactionCount(), "交易数量%s和区块交易数量%s不一致", transactionCount, blockInfo.getTransactionCount());
        }
	}
	
	/**
	 * 为通道绑定 ChaincodeEvent
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:43:43
	 */
	public String bindChaincodeEvent(Vector<BaseChaincodeEvent> chaincodeEvents, Channel channel) throws Exception {
		print("为通道绑定 ChaincodeEvent：%s", channel.getName());
		
		final Pattern CHAINCODE_ID_PATTERN = Pattern.compile(".*");
		final Pattern EVENT_NAME_PATTERN = Pattern.compile(Pattern.quote(EXPECTED_EVENT_NAME));
		
		ChaincodeEventListener chaincodeEventListener = new ChaincodeEventListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void received(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
				
				chaincodeEvents.add(new BaseChaincodeEvent(handle, blockEvent, chaincodeEvent));
				
                String es = blockEvent.getPeer() != null ? blockEvent.getPeer().getName() : blockEvent.getEventHub().getName();
                
                print("---------------------------------------------");
                print("已接收到Chaincode事件Handler: %s", handle);
                print("chaincodeId: %s, eventName: %s, txId: %s", chaincodeEvent.getChaincodeId(), chaincodeEvent.getEventName(), chaincodeEvent.getTxId());
                print("event payload: \\\"%s\\\", from eventhub: %s", new String(chaincodeEvent.getPayload()), es);
                print("---------------------------------------------");
			}
		};
		
		// 注册chaincode事件侦听器
		// chaincodeId模式和eventName模式都必须匹配才能调用chaincodeEventListener
		// 该侦听器将触发任何链接代码标识并仅针对EXPECTED_EVENT_NAME事件
        String handle = channel.registerChaincodeEventListener(CHAINCODE_ID_PATTERN, EVENT_NAME_PATTERN, chaincodeEventListener);
        print("事件句柄：%s，匹配事件：%s|%s", handle, CHAINCODE_ID_PATTERN, EVENT_NAME_PATTERN);
        
        // channel.unregisterChaincodeEventListener(handle);
        
        return handle;
	}
	
	public void checkChaincodeEvents(Channel channel, String chaincodeEventHandle, Vector<BaseChaincodeEvent> chaincodeEvents) throws Exception {
		try {
			if (chaincodeEventHandle != null) {
				// 两个监听接受者： 链码中的一个事件，以及两个事件中心的每个事件的两个通知
				channel.unregisterChaincodeEventListener(chaincodeEventHandle);
				
				// 事件总线中的监听事件数量 + Peer节点中的事件监听数量
				final int numberEventsExpected = channel.getEventHubs().size() + channel.getPeers(EnumSet.of(PeerRole.EVENT_SOURCE)).size();
				
				// 只要确保我们收到通知
				for (int i = 15; i > 0; --i) {
	                if (chaincodeEvents.size() == numberEventsExpected) {
	                    break;
	                } else {
	                    Thread.sleep(90); // 等待事件抵达
	                }
	            }
	            
	            for (BaseChaincodeEvent event : chaincodeEvents) {
	            	ChaincodeEvent codeEvent = event.getChaincodeEvent();
	            	
	            	checkArgument(StringUtils.equals(chaincodeEventHandle, event.getHandle()), "event handl 不一致：%s/%s", chaincodeEventHandle, event.getHandle());
	            	checkArgument(StringUtils.equals(EXPECTED_EVENT_NAME, codeEvent.getEventName()), "事件源 eventName 不一致：%s/%s", EXPECTED_EVENT_NAME, codeEvent.getEventName());
	            	checkArgument(Arrays.equals(EXPECTED_EVENT_DATA, codeEvent.getPayload()), "事件源 返回数据 不一致：%s/%s", EXPECTED_EVENT_DATA, codeEvent.getPayload());
	            	checkArgument(StringUtils.equals(CHAIN_CODE_NAME, codeEvent.getChaincodeId()), "事件 chaincode name 不一致：%s/%s", CHAIN_CODE_NAME, codeEvent.getChaincodeId());
	            	
	                BlockEvent blockEvent = event.getBlockEvent();
	                checkArgument(StringUtils.equals(channel.getName(), blockEvent.getChannelId()), "事件 channel name 不一致：%s/%s", channel.getName(), blockEvent.getChannelId());

	                print("ChaincodeEvent ccid: %s, event: %s, txId: %s, payload: %s", codeEvent.getChaincodeId(), codeEvent.getEventName(), codeEvent.getTxId(), new String(codeEvent.getPayload(), UTF_8));
	                print("BlockEvent: %s, %s, %s", blockEvent.getChannelId(), blockEvent.getTransactionCount(), blockEvent.getEnvelopeCount());
	            }
			} else {
				checkArgument(chaincodeEvents.isEmpty(), "存在chaincodeEvents监听对象");
			}
		} catch (Exception e) {
			e.printStackTrace();
            throw new RuntimeException("检查区块事件异常", e);
		}
	}
	
	protected Channel getChannel() throws Exception {
		userManager.initialize(ADMIN_NAME, ADMIN_SECRET, USER_NAME);
		
		Organization org = config.getOrganization(orgName);
		Channel channel = channelManager.initialize(channelName, org);
		
		return channel;
	}
	
	private static void print(String str, Object... args) {
		System.out.println(String.format(str, args));
	}
}
