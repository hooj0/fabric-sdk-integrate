package com.cnblogs.hoojo.fabric.sdk.examples;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;
import static org.hyperledger.fabric.sdk.Channel.TransactionOptions.createTransactionOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockInfo.EnvelopeInfo;
import org.hyperledger.fabric.sdk.BlockInfo.TransactionEnvelopeInfo;
import org.hyperledger.fabric.sdk.BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Channel.PeerOptions;
import org.hyperledger.fabric.sdk.Channel.TransactionOptions;
import org.hyperledger.fabric.sdk.ChannelConfiguration;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.Peer.PeerRole;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionInfo;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.TxReadWriteSetInfo;
import org.hyperledger.fabric.sdk.TxReadWriteSetInfo.NsRwsetInfo;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;
import org.junit.Before;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.event.BaseChaincodeEvent;
import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.model.OrganizationUser;
import com.cnblogs.hoojo.fabric.sdk.persistence.KeyValueFileStore;
import com.cnblogs.hoojo.fabric.sdk.util.GzipUtils;
import com.google.common.collect.Lists;

/**
 * `end 2 end` JavaSDK use API RestoreEnd2EndExamples
 * 恢复运行本示例而不需要重启网络 
 * 
 * 执行 invoke 方法 和 query 方法
 * 不安装、升级、实例化链码操作
 * 
 * @author hoojo
 * @createDate 2018年6月12日 下午4:31:43
 * @file End2EndExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.examples
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class RestoreEnd2EndExamples extends ApplicationLogging {

	private static final DefaultConfiguration config = DefaultConfiguration.getConfig();
	private Map<String, Properties> clientTLSProperties = new HashMap<>();
	
	private Collection<Organization> organizations;
	// KV 存储，这里实现的kv直接写入文件
	private KeyValueFileStore store;
	// KV 存储文件，生产库建议用数据库存储
	private File storeFile = new File("HFCSampletest.properties");

	private static final String ADMIN_NAME = "admin";
	private static final String ADMIN_SECRET = "adminpw";
	private static final String USER_NAME = "user1";
	
	private static final String FOO_CHANNEL_NAME = "foo";
	private static final String BAR_CHANNEL_NAME = "bar";

	private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
	private static final String EXPECTED_EVENT_NAME = "event";
	
	private static final Map<String, String> TX_EXPECTED;
	
	static {
		TX_EXPECTED = new HashMap<>();
		TX_EXPECTED.put("readset1", "Missing readset for channel bar block 1");
		TX_EXPECTED.put("writeset1", "Missing writeset for channel bar block 1");
	}

	String exampleName = "End2End Example Restore";

	String CHAIN_CODE_FILEPATH = "sdkintegration/gocc/sample1";
	String CHAIN_CODE_NAME = "example_cc_go";
	String CHAIN_CODE_PATH = "github.com/example_cc";
	String CHAIN_CODE_VERSION = "1";
	Type CHAIN_CODE_LANG = Type.GO_LANG;

	String testTxID = null; // save the CC invoke TxID and use in queries

	@Before
	public void checkConfig() throws Exception {
		out("\n\n\n~~~~~~~~~~~~RUNNING: %s~~~~~~~~~~~~~~~", exampleName);
		
		// out("reset default config");
		// DefaultConfiguration.resetConfig();
		
		out("Get all Organizations");
		organizations = config.getOrganizations();
		
		out("Run Initialize Ca Client");
		initializeCaClient();
	}

	@Test
	public void setup() throws Exception {
		
		// 使用本地文件做key-value数据保存的持久化操作
		if (!storeFile.exists()) { 
			throw new RuntimeException("store file not found!");
		}
		
		out("Create KeyValue File Store");
		// 创建key-value数据保存文件系统对象
		store = new KeyValueFileStore(storeFile);

		try {
			// This enrolls users with fabric ca and setups sample store to get users later.
			enrollOrganizationUsers(store);
			
			// Runs Fabric tests with constructing channels, joining peers, exercising chaincode
			runChaincode(store);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		out("~~~~~~~~~~examples run finished!!!~~~~~~~~~~~~~~");
	}
	
	private void runChaincode(KeyValueFileStore store) throws Exception {
		out("\nStart -> Run Chaincode \n");
		
		out("Create Hyperledger Fabric Client");
		//创建客户端实例
        HFClient client = HFClient.createNewInstance();
        // PKI密钥创建/签署/验证
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
		
        
        out("------Run Chaincode Peer: peerOrg1--------");
        //-----------------------------------------------------------------------------------
        Organization org = config.getOrganization("peerOrg1");
        
        // 准备通道：创建 Orderer服务、创建 channel、创建 Peer/Peer加入通道、添加EventHub、初始化通道
        Channel channel = prepareChannel(FOO_CHANNEL_NAME, client, org);

        out("save channel store");
        // 持久化保存
        store.saveChannel(channel);
        
        // 运行通道
        runChannel(channel, client, org, true, 0);
        
        out("shutdown & clean channel");
        checkArgument(!channel.isShutdown(), "通道已经关闭");
        // 强制通道关闭清理资源
        channel.shutdown(true); 
        checkArgument(channel.isShutdown(), "还未关闭通道");
        checkArgument(client.getChannel(FOO_CHANNEL_NAME) == null, "通道 %s 已被成功清理", FOO_CHANNEL_NAME);
       
        
        out("---------Run Chaincode Peer: peerOrg2----------");
        //-----------------------------------------------------------------------------------
        org = config.getOrganization("peerOrg2");
        
        channel = prepareChannel(BAR_CHANNEL_NAME, client, org);
        
        out("save channel store");
        // 持久化保存
        store.saveChannel(channel);
        
        runChannel(channel, client, org, true, 100); // 进行额度转移

        out("\n each & check Blockchain： %s ", channel.getName());
        eachCheckBlockchain(channel, client);

        checkArgument(!channel.isShutdown(), "通道已经关闭");
        checkArgument(channel.isInitialized(), "通道没有实例化");
        
        out("Finished -> Run Chaincode \n");
	}
	
	private void eachReadSet(NsRwsetInfo rwsetInfo, String channelId, long blockNumber) throws Exception {
		final String namespace = rwsetInfo.getNamespace();
		final KvRwset.KVRWSet rwset = rwsetInfo.getRwset();

		int rs = -1;
		// 遍历交易期间的读操作
		for (KvRwset.KVRead read : rwset.getReadsList()) {
			rs++;

			print("     ns: %s, read set: %d, key: %s,  version: [%d:%d]", namespace, rs, read.getKey(), read.getVersion().getBlockNum(), read.getVersion().getTxNum());

			if (BAR_CHANNEL_NAME.equals(channelId) && blockNumber == 2) {
				if (CHAIN_CODE_NAME.equals(namespace)) {
					if (rs == 0) {
						assertEquals("a", read.getKey());
						assertEquals(1, read.getVersion().getBlockNum());
						assertEquals(0, read.getVersion().getTxNum());
					} else if (rs == 1) {
						assertEquals("b", read.getKey());
						assertEquals(1, read.getVersion().getBlockNum());
						assertEquals(0, read.getVersion().getTxNum());
					} else {
						throw new RuntimeException(format("unexpected readset %d", rs));
					}

					TX_EXPECTED.remove("readset1");
				}
			}
		}
	}
	
	private void eachWriteSet(NsRwsetInfo rwsetInfo, String channelId, long blockNumber) throws Exception {
		final String namespace = rwsetInfo.getNamespace();
		final KvRwset.KVRWSet rwset = rwsetInfo.getRwset();

		int rs = -1;
		// 遍历交易期间的写入操作
		for (KvRwset.KVWrite write : rwset.getWritesList()) {
			rs++;

			String valAsString = printableString(new String(write.getValue().toByteArray(), "UTF-8"));
			print("     ns: %s, write set: %d, key: %s, value: '%s' ", namespace, rs, write.getKey(), valAsString);

			if (BAR_CHANNEL_NAME.equals(channelId) && blockNumber == 2) {
				if (rs == 0) {
					assertEquals("a", write.getKey());
					assertEquals("400", valAsString);
				} else if (rs == 1) {
					assertEquals("b", write.getKey());
					assertEquals("400", valAsString);
				} else {
					throw new RuntimeException(format("unexpected writeset %d", rs));
				}

				TX_EXPECTED.remove("writeset1");
			}
		}
	}
	
	private void eachNsReadWriteSet(int j, TransactionActionInfo transactionActionInfo, String channelId, long blockNumber) throws Exception {
		out("变量区块链 交易动作读写集 NsReadWriteSet 信息 ……");
		
		 // 获取事务读写集。 Eventhub事件将返回null。 对于eventhub事件，如果需要，按块号找到要读取的写入集。
        TxReadWriteSetInfo rwsetInfo = transactionActionInfo.getTxReadWriteSet();
        if (null != rwsetInfo) {
        	print("   tx action: %d, name space read write sets: %d", j, rwsetInfo.getNsRwsetCount());

        	// 遍历读写集
            for (TxReadWriteSetInfo.NsRwsetInfo nsRwsetInfo : rwsetInfo.getNsRwsetInfos()) {
            	eachReadSet(nsRwsetInfo, channelId, blockNumber);
            	eachWriteSet(nsRwsetInfo, channelId, blockNumber);
            }
        }
	}
	
	private void checkChaincodeEvent(TransactionActionInfo transactionActionInfo, long blockNumber) {
		out("检查区块链事件ChaincodeEvent信息 ……");
		
		// 检查我们是否有我们的预期的事件
        if (blockNumber == 2) {
            ChaincodeEvent chaincodeEvent = transactionActionInfo.getEvent();
            checkNotNull(chaincodeEvent, "区块2的交易动作事件为空");
            
            checkArgument(Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEvent.getPayload()), "区块2的交易结果不一致");
            print("chaincodeEvent: %s", json(chaincodeEvent));
        }
	}
	
	/**
	 * 变量并检查交易动作信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午10:25:49
	 */
	private void eachCheckTransactionAction(TransactionEnvelopeInfo transactionEnvelopeInfo, String channelId, long blockNumber) throws Exception {
		out("遍历区块链Transaction Action Info信息 ……");
		
		int j = 0;
        for (TransactionActionInfo transactionActionInfo : transactionEnvelopeInfo.getTransactionActionInfos()) {
            ++j;
            
            print("   tx action: %d, response status: %d", j, transactionActionInfo.getResponseStatus());
            checkArgument(transactionActionInfo.getResponseStatus() == Status.SUCCESS.getStatus(), "交易动作状态码错误");
            print("   tx action: %d, response message bytes as string: %s", j, printableString(new String(transactionActionInfo.getResponseMessageBytes(), "UTF-8")));
            print("   tx action: %d, endorsements count: %d", j, transactionActionInfo.getEndorsementsCount());
            checkArgument(2 == transactionActionInfo.getEndorsementsCount(), "交易动作背书次数错误");

            for (int n = 0; n < transactionActionInfo.getEndorsementsCount(); ++n) {
                BlockInfo.EndorserInfo endorserInfo = transactionActionInfo.getEndorsementInfo(n);
                print("Endorser: %d, signature: %s", n, Hex.encodeHexString(endorserInfo.getSignature()));
                print("Endorser: %d, mspid: %s, certificate: %s", n, endorserInfo.getMspid(), endorserInfo.getId());
            }
            
            print("   tx action: %d, chaincode arguments: %d", j, transactionActionInfo.getChaincodeInputArgsCount());
            for (int z = 0; z < transactionActionInfo.getChaincodeInputArgsCount(); ++z) {
            	print("     tx action: %d, chaincode argument: %d, values: %s", j, z, printableString(new String(transactionActionInfo.getChaincodeInputArgs(z), "UTF-8")));
            }

            print("   tx action: %d, proposal response status: %d", j, transactionActionInfo.getProposalResponseStatus());
            print("   tx action: %d, proposal response payload: %s", j, printableString(new String(transactionActionInfo.getProposalResponsePayload())));

            checkChaincodeEvent(transactionActionInfo, blockNumber);
            
            eachNsReadWriteSet(j, transactionActionInfo, channelId, blockNumber);
        }
	}
	
	/**
	 * 输出并检查交易信封信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午10:24:56
	 */
	private TransactionEnvelopeInfo echoCheckTransactionEnvelope(int i, BlockInfo.EnvelopeInfo envelopeInfo) throws Exception {
		out("输出区块链Transaction EnvelopeInfo信息 ……");
		
		BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;

        print("  tx number： %d, actions count: %d ", i, transactionEnvelopeInfo.getTransactionActionInfoCount());
        // 现在每个交易只有一个动作。
        checkArgument(transactionEnvelopeInfo.getTransactionActionInfoCount() == 1, "交易动作数量不正确: %s", transactionEnvelopeInfo.getTransactionActionInfoCount());
        print("  tx number: %d, isValid: %b", i, transactionEnvelopeInfo.isValid());
        checkArgument(transactionEnvelopeInfo.isValid(), "交易信封信息无效");
        print("  tx number: %d, validation code: %d", i, transactionEnvelopeInfo.getValidationCode());
        checkArgument(transactionEnvelopeInfo.getValidationCode() == 0, "交易信封验证代码错误: %s", transactionEnvelopeInfo.getValidationCode());
        
        return transactionEnvelopeInfo;
	}
	
	/**
	 * 变量并检查区块链信封信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午10:24:06
	 */
	@SuppressWarnings("deprecation")
	private void eachCheckEnvelopes(BlockInfo blockInfo, long blockNumber) throws Exception {
		out("遍历区块链 Envelope Infos信息 ……");
		
		int i = 0;
        int transactionCount = 0;
        for (EnvelopeInfo envelopeInfo : blockInfo.getEnvelopeInfos()) {
            ++i;

            out("  tx number: %d, txID: %s", i, envelopeInfo.getTransactionID());
            final String channelId = envelopeInfo.getChannelId();
            assertTrue("foo".equals(channelId) || "bar".equals(channelId));

            print("  tx number: %d, channel id: %s", i, channelId);
            print("  tx number: %d, epoch: %d", i, envelopeInfo.getEpoch());
            print("  tx number: %d, timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
            print("  tx number: %d, type id: %s", i, "" + envelopeInfo.getType());
            print("  tx number: %d, valid code: %s", i, "" + envelopeInfo.getValidationCode());
            print("  tx number: %d, nonce : %s", i, "" + Hex.encodeHexString(envelopeInfo.getNonce()));
            print("  tx number: %d, submitter mspid: %s,  certificate: %s", i, envelopeInfo.getCreator().getMspid(), envelopeInfo.getCreator().getId());

            // 交易类型
            if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) {
                ++transactionCount;
                
                TransactionEnvelopeInfo transactionEnvelopeInfo = echoCheckTransactionEnvelope(transactionCount, envelopeInfo);
                
                eachCheckTransactionAction(transactionEnvelopeInfo, channelId, blockNumber);
            }

            checkArgument(transactionCount == blockInfo.getTransactionCount(), "交易数量%s和区块交易数量%s不一致", transactionCount, blockInfo.getTransactionCount());
        }
	}
	
	/**
	 * 遍历并检查区块链信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午10:23:06
	 */
	private void eachCheckBlockchain(Channel channel, HFClient client) throws Exception {
		out("开始遍历并检查区块链信息 ……");
		
		try {
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
                
                print("block: %d, envelope count: %d", blockNumber, envelopeCount);
                
                eachCheckEnvelopes(blockInfo, blockNumber);
            }
            
            if (!TX_EXPECTED.isEmpty()) {
                throw new RuntimeException(TX_EXPECTED.values().iterator().next());
            }
        } catch (Exception e) {
        	logger.error("变量区块信息异常：", e);
            throw e;
        }
		
		out("遍历并检查区块链信息结束 ……");
	}
	
	/**
	 * 为通道绑定 ChaincodeEvent
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:43:43
	 */
	private String bindChaincodeEvent(Vector<BaseChaincodeEvent> chaincodeEvents, Channel channel) throws Exception {
		logger.info("为通道绑定 ChaincodeEvent：{}", channel.getName());
		
		final boolean isFooChain = FOO_CHANNEL_NAME.equals(channel.getName());
		
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
        logger.debug("事件句柄：{}，匹配事件：{}|{}", handle, CHAINCODE_ID_PATTERN, EVENT_NAME_PATTERN);
        
        
        // 对于非foo通道取消注册事件侦听器来测试事件不会被调用
        if (!isFooChain) {
        	channel.unregisterChaincodeEventListener(handle);
        	handle = null;
        }
        
        return handle;
	}
	
	/**
	 * 向实例化chaincode成功的通道响应Responses 发送交易请求
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:01:38
	 */
	private void sendTransaction2Orderer(Organization org, HFClient client, Channel channel, ChaincodeID chaincodeId, Collection<ProposalResponse> successful, int money) throws Exception {
		logger.info("执行Chaincode “调用和查询” 交易流程");
		
		Channel.NOfEvents nOfEvents = Channel.NOfEvents.createNofEvents();
        
        // 获取具有通道块事件监听的Peer
        Collection<Peer> peers = channel.getPeers(EnumSet.<Peer.PeerRole>of(PeerRole.EVENT_SOURCE));
        if (!peers.isEmpty()) {
        	nOfEvents.addPeers(peers);
        }
        if (!channel.getEventHubs().isEmpty()) {
        	nOfEvents.addEventHubs(channel.getEventHubs());
        }
        
        TransactionOptions options = createTransactionOptions(); // 交易选项
        options.userContext(client.getUserContext()); // 用户上下文。 默认值是客户端上的用户上下文。
        options.shuffleOrders(false); // 打乱 Orderers被尝试的顺序。 默认值是true。
        options.orderers(channel.getOrderers()); // 尝试进行此交易的 Orderers 共识，每个订单都会依次尝试成功提交。缺省值是尝试链上的所有Orderer。
        options.nOfEvents(nOfEvents); // 交易完成触发的事件
        
        // 将交易发送到具有指定用户上下文的Orderer。 如果没有事件中心或事件对等节点，则此交易立即完成，表明Orderer仅接受了交易。
        CompletableFuture<TransactionEvent> completableFuture = channel.sendTransaction(successful, options);
        logger.info("向Orderer节点——发起 执行Chaincode 交易：{}", json(options));
        logger.info("Proposal: {}", successful.iterator().next().getProposal());

        // 多线程模式
        Object result = completableFuture.thenApply(transactionEvent -> { 
        	waitOnFabric(0);
    		
    		// 必须是有效交易事件
    		checkArgument(transactionEvent.isValid(), "没有签名的交易事件");
    		// 必须有签名
    		checkNotNull(transactionEvent.getSignature(), "没有签名的交易事件");
    		// 必须有交易区块事件发生
    		BlockEvent blockEvent = checkNotNull(transactionEvent.getBlockEvent(), "交易事件的区块事件对象为空");
    		
    		logger.info("成功实例化Chaincode，本次实例化交易ID：{}", transactionEvent.getTransactionID());
    		try {
    			checkArgument(StringUtils.equals(blockEvent.getChannelId(), channel.getName()), "事件名称和对应通道名称不一致");
			} catch (Exception e) {
	            throw new RuntimeException(e);
			}
    		
    		testTxID = transactionEvent.getTransactionID();
    		
    		return queryChaincode(client, channel, chaincodeId, money);
        }).exceptionally(e -> {
        	
        	catchEventException(e);
        	return "exception " + e.getMessage();
        }).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        
        logger.debug("最终返回结果：{}", result);
	}
	
	/**
	 * 运行chaincode示例：包括install cc、instantiate cc、invoke cc、query cc、query block by peer、query blockInfo、check chaincode events
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:39:07
	 */
	private void runChannel(Channel channel, HFClient client, Organization org, boolean installChaincode, int money) throws Exception {
		out("Start -> Organization: %s , run channel: %s", org.getName(), channel.getName());
		
		Vector<BaseChaincodeEvent> chaincodeEvents = new Vector<>(); 
        
        out("register chaincode event listeners");
        String handle = bindChaincodeEvent(chaincodeEvents, channel);
        
        ChaincodeID chaincodeId = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setPath(CHAIN_CODE_PATH).setVersion(CHAIN_CODE_VERSION).build();
        out("ChaincodeID: %s", chaincodeId);
        
        if (installChaincode) {
        	client.setUserContext(org.getPeerAdmin());
        	
        	//out("install chaincode proposal");
        	//installChaincode(chaincodeId, client, channel);
        	
        	//out("Instantiate chaincode proposal");
        	//Collection<ProposalResponse> successful = instantiateChaincode(chaincodeId, client, channel, money);
            
        	Collection<ProposalResponse> successful = invokeChaincode(client, org, channel, chaincodeId);
        	
            out("Sending Transaction to orderer with a and b set to 100 and %s respectively", "" + (200 + money));
            sendTransaction2Orderer(org, client, channel, chaincodeId, successful, money);
            
            out("query blockInfo on Peers");
            queryBlockInfoByPeer(client, org, channel);
            
            out("query blockInfo on default");
            queryBlockInfo(client, org, channel);
            
            out("check chaincode events");
            checkChaincodeEvents(channel, handle, chaincodeEvents);
        }

        out("Finished -> Organization: %s , run channel: %s", org.getName(), channel.getName());
	}
	
	/**
	 *	查询整条链的区块信息，即 “区块链”的信息 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午9:41:48
	 */
	private BlockchainInfo queryBlockchainInfo(Channel channel) throws Exception {
		BlockchainInfo blockChain = channel.queryBlockchainInfo();
		print("Blockchain height: %s", blockChain.getHeight());
		
		String chainCurrentHash = Hex.encodeHexString(blockChain.getCurrentBlockHash());
        String chainPreviousHash = Hex.encodeHexString(blockChain.getPreviousBlockHash());
        print("Blockchain current block hash: %s", chainCurrentHash);
        print("Blockchain previous block hash: %s", chainPreviousHash);
        
        return blockChain;
	}
	
	/**
	 * 通过在区块链中的索引编号查询区块信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午9:42:34
	 */
	private BlockInfo queryBlockByNumber(Channel channel, BlockchainInfo blockChain, long number) throws Exception {
		
		BlockInfo latestBlockInfo = channel.queryBlockByNumber(number); // 应该返回最新的块，即块号2
        String previousHash = Hex.encodeHexString(latestBlockInfo.getPreviousHash());
        print("queryBlockByNumber blockNumber: %s , previous_hash: %s", latestBlockInfo.getBlockNumber(), previousHash);
        
        String chainPreviousHash = Hex.encodeHexString(blockChain.getPreviousBlockHash());
        checkArgument(blockChain.getHeight() - 1 == latestBlockInfo.getBlockNumber(), "查到的区块编号和返回的区块编号不一致");
		checkArgument(StringUtils.equals(chainPreviousHash, previousHash), "区块链的前一个Hash和最后一个区块的Hash不一致");
		
		return latestBlockInfo;
	}
	
	/**
	 * 通过块哈希查询区块信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午9:45:25
	 */
	private BlockInfo queryBlockByHash(Channel channel, BlockInfo block, long number) throws Exception {

		byte[] blockHash = block.getPreviousHash();
		BlockInfo blockInfo = channel.queryBlockByHash(blockHash);
		
		print("queryBlockByHash blockNumber: %s", blockInfo.getBlockNumber());
		checkArgument(number == blockInfo.getBlockNumber(), "查到的区块编号和返回的区块编号不一致");
		
		return blockInfo;
	}
	
	/**
	 * 通过 TransactionId 查询 区块信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午9:50:21
	 */
	private BlockInfo queryBlockByTxID(Channel channel, String txId, long number) throws Exception {
		
		BlockInfo blockInfo = channel.queryBlockByTransactionID(txId);
		print("queryBlockByTransactionID blockNumber: %s", blockInfo.getBlockNumber());
		
		checkArgument(number == blockInfo.getBlockNumber(), "查到的区块编号和返回的区块编号不一致");

		return blockInfo;
	}
	
	/**
	 * 通过交易Id 查询交易信息
	 * @author hoojo
	 * @createDate 2018年6月15日 上午9:55:17
	 */
	private TransactionInfo queryTransactionByID(Channel channel, String txId) throws Exception {
		
		TransactionInfo txInfo = channel.queryTransactionByID(txId);
		print("queryTransactionByID txID: %s, validation code: %s ", txInfo.getTransactionID(), txInfo.getValidationCode().getNumber());
		
		return txInfo;
	}
	
	private void checkChaincodeEvents(Channel channel, String chaincodeEventHandle, Vector<BaseChaincodeEvent> chaincodeEvents) throws Exception {
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
	            checkArgument(numberEventsExpected == chaincodeEvents.size(), "事件数量不一致");
	            
	            for (BaseChaincodeEvent event : chaincodeEvents) {
	            	ChaincodeEvent codeEvent = event.getChaincodeEvent();
	            	
	            	checkArgument(StringUtils.equals(chaincodeEventHandle, event.getHandle()), "event handl 不一致：%s/%s", chaincodeEventHandle, event.getHandle());
	            	checkArgument(StringUtils.equals(testTxID, codeEvent.getTxId()), "交易ID不一致：%s/%s", testTxID, codeEvent.getTxId());
	            	checkArgument(StringUtils.equals(EXPECTED_EVENT_NAME, codeEvent.getEventName()), "事件源 eventName 不一致：%s/%s", EXPECTED_EVENT_NAME, codeEvent.getEventName());
	            	checkArgument(Arrays.equals(EXPECTED_EVENT_DATA, codeEvent.getPayload()), "事件源 返回数据 不一致：%s/%s", EXPECTED_EVENT_DATA, codeEvent.getPayload());
	            	checkArgument(StringUtils.equals(CHAIN_CODE_NAME, codeEvent.getChaincodeId()), "事件 chaincode name 不一致：%s/%s", CHAIN_CODE_NAME, codeEvent.getChaincodeId());
	            	
	                BlockEvent blockEvent = event.getBlockEvent();
	                checkArgument(StringUtils.equals(channel.getName(), blockEvent.getChannelId()), "事件 channel name 不一致：%s/%s", channel.getName(), blockEvent.getChannelId());

	                print("ChaincodeEvent: %s", json(codeEvent));
	                print("BlockEvent: %s", json(blockEvent));
	            }
			} else {
				checkArgument(chaincodeEvents.isEmpty(), "存在chaincodeEvents监听对象");
			}
		} catch (Exception e) {
			logger.error("{} 检查区块事件异常 : ", channel.getName(), e);
            throw new RuntimeException("检查区块事件异常", e);
		}
	}
	
	private void queryBlockInfo(HFClient client, Organization org, Channel channel) throws Exception {
		out("查询通道区块开始：%s", channel.getName());
		
		try {
			// 查询区块链
			BlockchainInfo blockChain = queryBlockchainInfo(channel);
	        
	        // 按区块号查询
			BlockInfo latestBlockInfo = queryBlockByNumber(channel, blockChain, blockChain.getHeight() - 1);
	        
	        // 通过块哈希查询。 使用最新块的前一个散列，所以应该返回第1个块
			queryBlockByHash(channel, latestBlockInfo, blockChain.getHeight() - 2);
			
			// 通过TxID查询块。 由于它是最后一个TxID，应该是块2
			queryBlockByTxID(channel, testTxID, blockChain.getHeight() - 1);
			
			// 通过TxId 查询交易信息
			queryTransactionByID(channel, testTxID);
			
		} catch (Exception e) {
            logger.error("{} 查询区块异常 : ", channel.getName(), e);
            throw new RuntimeException("查询区块异常", e);
		}
		
		out("查询通道区块结束：", channel.getName());
	}
	
	public void queryBlockInfoByPeer(HFClient client, Organization org, Channel channel) {
		try {
			// 我们只能将通道查询发送给与SDK用户上下文处于相同组织的对等节点。从当前正在使用的org中获取对等节点并随机选择一个发送查询。
			String peerName = org.getPeerNames().iterator().next();
			Collection<Peer> peers = channel.getPeers();
			
			Peer orgPeer = null;
			for (Peer peer : peers) {
				if (StringUtils.equals(peer.getName(), peerName)) {
					orgPeer = peer;
					print("# 找到组织中对应通道中的对等节点：%s", peer.getName());
				}
				
				if (StringUtils.equals(peer.getName(), peerName) && StringUtils.equals(peer.getUrl(), org.getPeerLocation(peerName))) {
					orgPeer = peer;
					print("* 找到组织中对应通道中的对等节点：%s", peer.getName());
				}
			}
			
			print("使用组织的对等节点：%s", orgPeer.getName());
		} catch (Exception e) {
			logger.error("{} 通过Peer查询区块异常 : ", channel.getName(), e);
            throw new RuntimeException("通过Peer查询区块异常", e);
		}
	}
	
	private void catchEventException(Throwable e) {
		if (e instanceof TransactionEventException) {
            BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
            if (te != null) {
                throw new AssertionError(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()), e);
            }
        }

        throw new AssertionError(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()), e);
	}
	
	/**
	 * 执行 query 查询 chaincode 业务
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:44:29
	 */
	private String queryChaincode(HFClient client, Channel channel, ChaincodeID chaincodeId, int money) {
		logger.info("在通道：{} 发起chaincode 查询业务：{}", channel.getName(), chaincodeId);
		
		String payload = null;
		try {
			// 构建查询请求
			QueryByChaincodeRequest request = client.newQueryProposalRequest();
			request.setChaincodeID(chaincodeId);
			request.setProposalWaitTime(config.getProposalWaitTime());
			request.setArgs(new String[] { "b" });
			request.setFcn("query");
			
			Map<String, byte[]> transientMap = new HashMap<>();
            transientMap.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
            transientMap.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
            request.setTransientMap(transientMap);
            
            // 向所有Peer节点发送查询请求
            Collection<ProposalResponse> responses = channel.queryByChaincode(request, channel.getPeers());
            logger.info("向 channel.Peers——发起Chaincode查询请求：{}", json(request));
            
            for (ProposalResponse response : responses) {
                if (!response.isVerified() || response.getStatus() != Status.SUCCESS) {
                    throw new RuntimeException("查询失败， peer " + response.getPeer().getName() + "， status: " + response.getStatus() + ". Messages: " + response.getMessage() + ". Was verified : " + response.isVerified());
                } else {
                    payload = response.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    print("查询来自对等点：%s ，返回金额：%s，交易金额：%s", response.getPeer().getName(), payload, (300 + money));
                }
            }
		} catch (Exception e) {
			logger.error("调用chaincode时发生异常：", e);
            throw new RuntimeException("调用chaincode时发生异常： " + e.getMessage());
		}

		return payload;
	}
	
	/**
	 * 执行invoke调用chaincode业务
	 * @author hoojo
	 * @createDate 2018年6月15日 下午2:43:04
	 */
	private Collection<ProposalResponse> invokeChaincode(HFClient client, Organization org, Channel channel, ChaincodeID chaincodeId) throws RuntimeException {
		logger.info("在通道：{}，发起调用Chaincode 交易业务: {}", channel.getName(), chaincodeId);
		
		Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        
		try {
            client.setUserContext(org.getUser(USER_NAME));
            
            // 构建——交易提议请求，向所有对等节点发送
            TransactionProposalRequest request = client.newTransactionProposalRequest();
            request.setChaincodeID(chaincodeId);
            request.setChaincodeLanguage(CHAIN_CODE_LANG);
            request.setProposalWaitTime(config.getProposalWaitTime());
            request.setFcn("move");
            request.setArgs("a", "b", "100");
            
            // 添加——到分类账的提案中的瞬时数据
            Map<String, byte[]> transientMap = new HashMap<>();
            transientMap.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
            transientMap.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
            transientMap.put("result", ":)".getBytes(UTF_8));  // This should be returned see chaincode why.
            transientMap.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);  //This should trigger an event see chaincode why.

            request.setTransientMap(transientMap);
            
            // 发送——交易请求
            Collection<ProposalResponse> responses = channel.sendTransactionProposal(request, channel.getPeers());
            logger.info("向 channel.Peers节点——发起交易“提议”请求，参数: {}", json(request));
            
			for (ProposalResponse response : responses) {
				if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
					print("交易成功 Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
					successful.add(response);
				} else {
					print("交易失败 Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
					failed.add(response);
				}
			}
			
			// 检查所有响应是否一致。 我们应该只有一组上述所有建议都一致的组。 请注意，发送到Orderer时会自动完成。
			// 这里以应用程序可以调用和选择的示例显示。
			// 请参阅org.hyperledger.fabric.sdk.proposal.consistency_validation配置属性。
			
			// 检查请求——响应结果有效且不为空
			Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(responses);
            if (proposalConsistencySets.size() != 1) {
                throw new RuntimeException(format("成功响应请求结果的数量等于1，实际响应数量： %d", proposalConsistencySets.size()));
            }
            logger.info("接收交易请求响应： {} ，Successful+verified: {}， Failed: {}", responses.size(), successful.size(), failed.size());
            
            if (failed.size() > 0) {
                ProposalResponse firstResponse = failed.iterator().next();
                throw new RuntimeException("没有足够的背书节点调用: " + failed.size() + "， endorser error: " + firstResponse.getMessage() + ". Was verified: " + firstResponse.isVerified());
            }
            
            ProposalResponse response = successful.iterator().next();
            // 对应上面构建的 transientMap->result
            byte[] chaincodeBytes = response.getChaincodeActionResponsePayload(); // 链码返回的数据
            String resultAsString = null;
            if (chaincodeBytes != null) {
                resultAsString = new String(chaincodeBytes, UTF_8);
            }
            checkArgument(StringUtils.equals(":)", resultAsString), "%s :和定义的账本数据不一致", resultAsString);
            checkState(response.getChaincodeActionResponseStatus() == Status.SUCCESS.getStatus(), "%s：非正常的响应状态码", response.getChaincodeActionResponseStatus());
            
            TxReadWriteSetInfo readWriteSetInfo = response.getChaincodeActionResponseReadWriteSetInfo();
            checkNotNull(readWriteSetInfo, "提议请求响应的读写集为空");
            checkArgument(readWriteSetInfo.getNsRwsetCount() > 0, "提议请求读写集数量为空");
            
            ChaincodeID codeId = response.getChaincodeID();
            checkNotNull(codeId, "提议请求响应ChaincodeID为空");
            checkArgument(StringUtils.equals(CHAIN_CODE_NAME, codeId.getName()), "chaincode 名称不一致");
            checkArgument(StringUtils.equals(CHAIN_CODE_VERSION, codeId.getVersion()), "chaincode 版本不一致");
            
            final String path = codeId.getPath();
            if (null == CHAIN_CODE_PATH) {
                checkArgument(StringUtils.isBlank(path), "chaincode Path不为空");
            } else {
            	checkArgument(StringUtils.equals(CHAIN_CODE_PATH, path), "chaincode Path不一致");
            }

            //logger.info("向Orderer发起Chaincode Invoke交易：{}", json(response));
            // 底层会将交易发生给选择一个Orderer
            //channel.sendTransaction(successful).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
            return responses;
		} catch (Exception e) {
            logger.error("调用chaincode时发生异常：", e);
            throw new RuntimeException("调用chaincode时发生异常： " + e.getMessage());
		}
	}

	
	/**
	 * 准备通道：创建 Orderer服务、创建 channel、创建 Peer/Peer加入通道、添加EventHub、初始化通道
	 * @author hoojo
	 * @createDate 2018年6月13日 下午4:05:22
	 * @param channelName 通道名称
	 * @param client HFClient
	 * @param org Organization
	 * @return Channel
	 * @throws Exception
	 */
	private Channel prepareChannel(String channelName,  HFClient client, Organization org) throws Exception {
		out("PrepareChannel -> Organization: %s , Constructing channel: %s", org.getName(), channelName);
		
		/** 设置 peer 管理员User上下文 */
		client.setUserContext(org.getPeerAdmin());
		
		out("create orderer service");
		/** 创建 Orderer 共识服务 */
		List<Orderer> orderers = createOrderer(client, org);
		
		/** 选择第一个 Orderer 创建通道 */
		Orderer anOrderer = orderers.iterator().next();
		
		out("remove choose orderer service");
		/** 剔除已选择 Orderer */
		orderers.remove(anOrderer);
        
		out("Cache find channel: %s", channelName);
		Channel channel = store.getChannel(client, channelName);
		
		if (channel == null) {
			out("Created channel: %s", channelName);
			/** 创建通道 */
			channel = createChannel(channelName, anOrderer, client, org);

			out("Created Peer Join Channel: %s", channelName);
			/** 创建 peer，channel加入Peer  */
			createPeerThenJoin(channel, client, org);
			
			out("Add Orderer to Channel: %s", channelName);
			/** 为通道添加其他 Orderer服务 */
			for (Orderer orderer : orderers) {
				channel.addOrderer(orderer);
				logger.trace("Add Channel Orderer: {}->{}", orderer.getName(), orderer.getUrl());
			}
			
			out("Add EventHub: %s", channelName);
			/** 添加事件总线 */
			addEventHub(channel, client, org);
			
			out("initialize channel: %s", channelName);
			/** 初始化 */
			channel.initialize();
			out("Organization: %s , Finished initialization channel： %s", org.getName(), channelName);
		}
        
		return checkChannelSerialize(channel, client);
	}
	
	/**
	 * 创建Orderer服务
	 * @author hoojo
	 * @createDate 2018年6月13日 下午4:33:20
	 */
	private List<Orderer> createOrderer(HFClient client, Organization org) throws Exception {
		logger.info("开始创建 Orderer 服务……");
		List<Orderer> orderers = Lists.newLinkedList();
		
		for (String ordererName : org.getOrdererNames()) {
			String grpcURL = org.getOrdererLocation(ordererName);
			logger.info("构建 Orderer 服务：{}，URL：{}", ordererName, grpcURL);
			
			Properties ordererProps = config.getOrdererProperties(ordererName);
            // 5分钟以下需要更改服务器端才能接受更快的Ping速率。
			ordererProps.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] { 5L, TimeUnit.MINUTES });
			// 设置keepAlive以避免不活动http2连接超时的示例。
			ordererProps.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] { 8L, TimeUnit.SECONDS });
			ordererProps.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[] { true });
			
			Orderer orderer = client.newOrderer(ordererName, grpcURL, ordererProps);
			orderers.add(orderer);
		}
		
		return orderers;
	}
	
	/**
	 * 通过channel.tx配置文件，创建通道
	 * @author hoojo
	 * @createDate 2018年6月13日 下午4:29:53
	 * @throws Exception
	 */
	private Channel createChannel(String channelName, Orderer anOrderer, HFClient client, Organization org) throws Exception {
		logger.info("开始创建通道：{}", channelName);
		
		// 通道配置文件
        File channelFile = new File(config.getChannelPath(), channelName + ".tx");
        logger.debug("通道配置文件：{}", channelFile.getAbsolutePath());
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(channelFile);
        
        byte[] channelConfigurationSignatures = client.getChannelConfigurationSignature(channelConfiguration, org.getPeerAdmin());
		// 创建只有一个管理员(orgs peer admin)签名的通道。 如果通道创建策略需要更多签名，则需要添加更多管理员签名。
        Channel channel = client.newChannel(channelName, anOrderer, channelConfiguration, channelConfigurationSignatures);
        logger.info("创建通道：{}，配置：{}", channel.getName(), channelFile.getAbsolutePath());
        
        return channel;
	}
	
	/**
	 * 创建peer节点，然后加入该Peer节点
	 * @author hoojo
	 * @createDate 2018年6月13日 下午4:21:51
	 */
	private void createPeerThenJoin(Channel channel, HFClient client, Organization org) throws Exception {
		logger.info("开始创建 peer/加入Peer：{}", channel.getName());
		
		boolean doPeerEventing = !config.isRunningAgainstFabric10() && BAR_CHANNEL_NAME.equals(channel.getName());
		boolean everyother = true; // 测试不同的角色的事件模式
		
		for (String peerName : org.getPeerNames()) {
			String grpcURL = org.getPeerLocation(peerName);
			logger.info("创建对等节点:{}，URL：{}", peerName, grpcURL);

			Properties peerProps = config.getPeerProperties(peerName);
			peerProps.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

			// 创建节点
			Peer peer = client.newPeer(peerName, grpcURL, peerProps);

			PeerOptions options = PeerOptions.createPeerOptions();
			if (doPeerEventing && everyother) {
				// 默认 所有角色
			} else {
				// 除事件源外的所有角色
				options.setPeerRoles(PeerRole.NO_EVENT_SOURCE);
			}
			logger.info("对等节点:{}，角色：{}， 加入通道：{}", peerName, options.getPeerRoles(), channel.getName());
			// 加入通道
			channel.joinPeer(peer, options);
			logger.debug("节点: {} 加入通道：{}", peerName, channel.getName());

			everyother = !everyother;
		}
        
		//just for testing ...
		// 测试事件模式下，确定至少有一种角色类型的对等节点
        if (doPeerEventing) {
        	// foo -> 角色：[ENDORSING_PEER, CHAINCODE_QUERY, LEDGER_QUERY]
        	if (!channel.getPeers(EnumSet.of(PeerRole.EVENT_SOURCE)).isEmpty()) {
        		logger.trace("peer 包含角色：", PeerRole.EVENT_SOURCE);
        	} else {
        		throw new RuntimeException("peer 不包含角色： EVENT_SOURCE");
        	}

        	if (!channel.getPeers(PeerRole.NO_EVENT_SOURCE).isEmpty()) {
        		logger.trace("peer 包含角色：", PeerRole.NO_EVENT_SOURCE);
        	} else {
        		throw new RuntimeException("peer 不包含角色：NO_EVENT_SOURCE");
        	}
        }
	}
	
	/**
	 * 添加事件总线
	 * @author hoojo
	 * @createDate 2018年6月13日 下午4:15:45
	 */
	private void addEventHub(Channel channel, HFClient client, Organization org) throws Exception {
		logger.info("开始添加事件总线：{}", channel.getName());
		
		for (String eventHubName : org.getEventHubNames()) {
        	String grpcURL = org.getEventHubLocation(eventHubName);
        	logger.info("添加事件监听：{}，URL：{}", eventHubName, grpcURL);
        	
        	Properties eventHubProps = config.getEventHubProperties(eventHubName);
        	
			eventHubProps.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] { 5L, TimeUnit.MINUTES });
			eventHubProps.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] { 8L, TimeUnit.SECONDS });
        	
        	EventHub eventHub = client.newEventHub(eventHubName, grpcURL, eventHubProps);
        	logger.trace("Add EventHub name: {}, url: {}, conntime: {}", eventHub.getName(), eventHub.getUrl(), eventHub.getConnectedTime());

        	channel.addEventHub(eventHub);
        }
	}
	
	/**
	 * 检查“序列化/反序列化”通道。
	 * 可以进行持久化存储，方便下次直接从缓存中恢复通道
	 * @author hoojo
	 * @throws IOException 
	 * @throws org.hyperledger.fabric.sdk.exception.InvalidArgumentException 
	 * @createDate 2018年6月13日 下午4:26:08
	 */
	private Channel checkChannelSerialize(Channel channel, HFClient client) throws Exception {
		logger.info("检查通道可否序列化：{}", channel.getName());
		
		if (!channel.isInitialized()) {
			logger.warn("通道还未初始化操作");
		}
		if (channel.isShutdown()) {
			logger.warn("通道已经关闭");
		}
		
		// 检查通道是否可以序列化，可以进行持久化存储，方便下次直接从缓存中恢复通道
        byte[] serializedChannelBytes = channel.serializeChannel();
        // 关闭所有释放资源的频道
        channel.shutdown(true);
        logger.debug("serializedChannelBytes: {}", serializedChannelBytes.length);
        
        // 从通道序列化数据中恢复通道
        channel = client.deSerializeChannel(serializedChannelBytes).initialize();
        
        checkState(channel.isInitialized(), "通道未初始化");
        checkState(!channel.isShutdown(), "通道被关闭");
        
        return channel;
	}
	
	/**
	 * 启动前 设置每个 org资源的 ca 客户端对象
	 * @author hoojo
	 * @createDate 2018年6月12日 下午5:44:48
	 * @throws MalformedURLException
	 * @throws InvalidArgumentException
	 */
	private void initializeCaClient() throws Exception {
		logger.info("初始化 CA 客户端……");
		
		// 创建 CA client
		for (Organization org : organizations) {
			String caName = org.getCAName(); // Try one of each name and no name.
			if (StringUtils.isNotBlank(caName)) {
				org.setCAClient(HFCAClient.createNewInstance(caName, org.getCALocation(), org.getCAProperties()));
			} else {
				org.setCAClient(HFCAClient.createNewInstance(org.getCALocation(), org.getCAProperties()));
			}
		}
	}
	
	/**
	 * 从Fabric CA获取客户端TLS证书，使用客户端TLS证书
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:38:55
	 * @throws EnrollmentException
	 * @throws InvalidArgumentException
	 * @throws IOException
	 */
	private void enrollTLS(KeyValueFileStore store, Organization org) throws EnrollmentException, InvalidArgumentException, IOException {
		logger.info("管理员 TLS 模式——认证……");
		
		HFCAClient ca = org.getCAClient();
		
		// 构建认证请求
		final EnrollmentRequest request = new EnrollmentRequest();
		request.addHost("localhost");
		request.setProfile("tls");
		logger.trace("ca admin request: {}", json(request));
		
		// 发起请求进行认证
		final Enrollment enrollment = ca.enroll(ADMIN_NAME, ADMIN_SECRET, request); // 设置 ca 管理员名称和密码，对应 docker-compose.yaml
		final String tlsCertPEM = enrollment.getCert();
		final String tlsKeyPEM = getPEMString(enrollment.getKey());
		
		logger.trace("enrollment: {}", enrollment);
		logger.trace("tlsKeyPEM: {}, tlsCertPEM: {}", tlsKeyPEM, tlsCertPEM);
		
		final Properties clientProps = new Properties();
		clientProps.put("clientCertBytes", tlsCertPEM.getBytes(UTF_8));
		clientProps.put("clientKeyBytes", tlsKeyPEM.getBytes(UTF_8));
		
		clientTLSProperties.put(org.getName(), clientProps);
		
		// 保存证书 key、cert
		store.storeClientPEMTLSCertificate(org, tlsCertPEM);
		store.storeClientPEMTLSKey(org, tlsKeyPEM);
	}
	
	/**
	 * ca Admin 角色 - 管理员认证
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:41:54
	 * @throws EnrollmentException
	 * @throws InvalidArgumentException
	 */
	private OrganizationUser enrollAdmin(KeyValueFileStore store, Organization org) throws EnrollmentException, InvalidArgumentException {
		logger.info("管理员CA——认证……");
		
		HFCAClient ca = org.getCAClient();
		
		// 从缓存或store中获取用户
		OrganizationUser admin = store.getMember(ADMIN_NAME, org.getName());
		if (!admin.isEnrolled()) { // 未认证，只需用用ca client进行认证
			
			// 认证：获取用户的签名证书和私钥。
			Enrollment enrollment = ca.enroll(admin.getName(), ADMIN_SECRET);
			logger.trace("用户：{} 进行认证: {}", admin.getName(), json(enrollment));
			
			//admin.setEnrollmentSecret(ADMIN_SECRET);
			admin.setEnrollment(enrollment);
			admin.setMspId(org.getMSPID());
		}
		
		return admin;
	}
	
	/**
	 * User 角色-普通用户注册和认证
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:48:39
	 * @param store
	 * @param org
	 * @return
	 * @throws Exception
	 */
	private OrganizationUser registerAndEnrollUser(KeyValueFileStore store, Organization org) throws Exception {
		logger.info("普通用户——注册和认证……");
		
		HFCAClient ca = org.getCAClient();
		
		// 从缓存或store中获取用户
		OrganizationUser user = store.getMember(USER_NAME, org.getName());
		if (!user.isRegistered()) { // 未注册
			// 用户注册
			final RegistrationRequest request = new RegistrationRequest(user.getName(), "org1.department1");
			logger.trace("register request: {}",  json(request));
			
			// 利用管理员权限进行普通user注册
			String secret = ca.register(request, org.getAdmin());
			logger.trace("用户 {} 注册，秘钥：{}", user, secret);
			
			user.setAffiliation(request.getAffiliation());
			user.setEnrollmentSecret(secret);
		}
		
		if (!user.isEnrolled()) { // 未认证
			// 用户认证
			Enrollment enrollment = ca.enroll(user.getName(), user.getEnrollmentSecret());
			logger.trace("用户：{} 进行认证: {}", user.getName(), json(enrollment));
			
			user.setEnrollment(enrollment);
			user.setMspId(org.getMSPID());
		}
		
		return user;
	}
	
	/**
	 * peer节点管理员证书私钥封装
	 * @author hoojo
	 * @createDate 2018年6月13日 上午10:53:35
	 * @throws IOException
	 */
	private OrganizationUser wrapperPeerAdmin(KeyValueFileStore store, Organization org) throws EnrollmentException, InvalidArgumentException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, IOException {
		logger.info("节点管理员——认证……");
		
		final String orgName = org.getName();
		final String mspid = org.getMSPID();
		final String domain = org.getDomainName();
		
		// src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/
		File keydir = Paths.get(config.getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/keystore", domain)).toFile();
		File privateKeyFile = GzipUtils.findFileSk(keydir);
		File certificateFile = Paths.get(config.getCryptoTxConfigRootPath(), "crypto-config/peerOrganizations/", domain, format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", domain, domain)).toFile();

		logger.trace("privateKeyDir: {}", keydir.getAbsolutePath());
		logger.trace("privateKeyFile: {}", privateKeyFile.getAbsolutePath());
		logger.trace("certificateFile: {}", certificateFile.getAbsolutePath());
		
		// 从缓存或store中获取用户
		OrganizationUser peerAdmin = store.getMember(orgName + "Admin", orgName, mspid, privateKeyFile, certificateFile);
		logger.trace("构建Peer Admin用户：{}", peerAdmin);
		
		return peerAdmin;
	}
	
	private String getPEMString(PrivateKey privateKey) throws IOException {
		StringWriter stringWriter = new StringWriter();
		
		JcaPEMWriter writer = new JcaPEMWriter(stringWriter);
		writer.writeObject(privateKey);
		writer.close();
		
		return writer.toString();
	}
	
	/**
	 * CA Admin、User、Peer Admin、TLS 注册和认证
	 * @author hoojo
	 * @createDate 2018年6月13日 上午11:01:15
	 * @throws Exception
	 */
	private void enrollOrganizationUsers(KeyValueFileStore store) throws Exception {
		out("Start -> Enroll Organization: CA Admin、User、Peer Admin、TLS ");
		
		for (Organization org : organizations) {
			logger.info("orgName: {} / mspID: {} 进行用户注册和认证", org.getName(), org.getMSPID());
			
			HFCAClient ca = org.getCAClient();
			// PKI密钥创建/签署/验证
			ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
			
			/** TLS 证书模式：如何从Fabric CA获取客户端TLS证书，为orderer、peer 使用一个客户端TLS证书 */
			if (config.isRunningFabricTLS()) {
				enrollTLS(store, org);
			} else {
				logger.debug("TLS 证书模式：{}", config.isRunningFabricTLS());
			}
			
			HFCAInfo info = ca.info();
			logger.debug("ca info: {}", json(info));
			
			checkNotNull(info, "HFCAInfo is null");
			if (!StringUtils.isBlank(info.getCAName())) {
				checkArgument(StringUtils.equals(info.getCAName(), ca.getCAName()), "HFCAInfo.CAName 和  CaInfo.CAName 不等");
			}
			
			// admin enroll
			OrganizationUser admin = enrollAdmin(store, org);
			
			// 设置当前组织 admin
			org.setAdmin(admin);
			
			// user register/enroll 
			OrganizationUser user = registerAndEnrollUser(store, org);
			
			// 设置当前组织 user
			org.addUser(user);
			
			// peer admin
			OrganizationUser peerAdmin = wrapperPeerAdmin(store, org);
			
			// 设置当前组织 peerAdmin
			org.setPeerAdmin(peerAdmin);
		}
		
		out("Finished -> Enroll Organization: CA Admin、User、Peer Admin、TLS ");
	}

	private void waitOnFabric(int additional) {

    }

	private static void out(String format, Object... args) {
		System.err.flush();
		System.out.flush();
		System.out.println("\n\n" + format(format, args));
		System.out.println("---------------------------------------------------------------------");
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
	
	// CHECKSTYLE.ON: Method length is 320 lines (max allowed is 150).
	private static String printableString(final String string) {
		int maxLogStringLength = 64;
		if (string == null || string.length() == 0) {
			return string;
		}

		String ret = string.replaceAll("[^\\p{Print}]", "?");
		ret = ret.substring(0, Math.min(ret.length(), maxLogStringLength)) + (ret.length() > maxLogStringLength ? "..." : "");
		return ret;
	}
	
	private String json(Object o) {
		if (o != null)
			return o.toString();
			//return new Gson().toJson(o);
		
		return "NULL";
	}
}
