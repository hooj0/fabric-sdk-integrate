package com.cnblogs.hoojo.fabric.sdk.updated;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.protos.peer.Query.ChaincodeInfo;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.NetworkConfig.CAInfo;
import org.hyperledger.fabric.sdk.NetworkConfig.UserInfo;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.util.TestUtils;
import com.cnblogs.hoojo.fabric.sdk.util.TestUtils.MockUser;

/**
 *  网络配置YAML（/ JSON）文件的集成测试
	此测试要求先前已运行End2endIT以设置通道。
  	它不依赖任何其他集成测试。
	也就是说，它可以运行或不运行其他端到端测试（除End2EndIT之外）。

  	此外，它可以执行多次而无需重启区块链。
  	另一个要求是网络配置文件与由End2endIT设置的拓扑相匹配。
	它首先检查“foo”频道并检查CHAIN_CODE_NAME是否已在频道上实例化，
	如果不是，则会使用该名称部署链接代码。
 * @author hoojo
 * @createDate 2018年6月20日 下午3:27:11
 * @file NetworkConfigTest.java
 * @package com.cnblogs.hoojo.fabric.sdk.examples
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class NetworkConfigTest {

	private static final DefaultConfiguration CONFIG = DefaultConfiguration.getConfig();

	private static final String ORG_NAME = "Org1";

	private static final String TEST_FIXTURES_PATH = "src/test/fixture";
	private static final String CHAIN_CODE_PATH = "github.com/example_cc";
	private static final String CHAIN_CODE_NAME = "cc-NetworkConfigTest-001";
	private static final String CHAIN_CODE_VERSION = "1";
	
	private static final String FOO_CHANNEL_NAME = "foo";

	//private static final TestConfigHelper configHelper = new TestConfigHelper();

	private static NetworkConfig networkConfig;
	private static Map<String, User> ORG_REGISTERED_USERS = new HashMap<>();

	@BeforeClass
	public static void doMainSetup() throws Exception {
		out("\n\n\nRUNNING: NetworkConfigIT.\n");

		//resetConfig();
		//configHelper.customizeConfig();

		try {
			setTLSProps();
			
			checkCaClient(ORG_NAME);
			
			checkCaClient("Org2");
			
			deployChaincodeIfRequired();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testUpdate1() throws Exception {

		// Setup client and channel instances
		HFClient client = getClient();
		
		Channel channel = constructChannel(client, FOO_CHANNEL_NAME);
		final String channelName = channel.getName();
		final ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION).setPath(CHAIN_CODE_PATH).build();

		out("Running testUpdate1 - Channel %s", channelName);

		int moveAmount = 5;
		String originalVal = queryChaincodeForCurrentValue(client, channel, chaincodeID);
		String newVal = "" + (Integer.parseInt(originalVal) + moveAmount);

		out("Original value = %s", originalVal);

		// user registered user
		client.setUserContext(ORG_REGISTERED_USERS.get(ORG_NAME)); // only using org1

		// Move some assets
		transferAmount(client, channel, chaincodeID, "a", "b", "" + moveAmount, null).thenApply(transactionEvent -> {
			out("################# %s", transactionEvent);
			
			// Check that they were moved
			queryChaincodeForExpectedValue(client, channel, newVal, chaincodeID);
			
			return null;
		}).thenApply(transactionEvent -> {
			out("################# %s", transactionEvent);

			// Move them back
			try {
				return transferAmount(client, channel, chaincodeID, "b", "a", "" + moveAmount, null).get(CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		}).thenApply(transactionEvent -> {
			out("################# %s", transactionEvent);

			// Check that they were moved back
			queryChaincodeForExpectedValue(client, channel, originalVal, chaincodeID);
			
			return null;
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

		}).get(CONFIG.getTransactionWaitTime(), TimeUnit.SECONDS);

		channel.shutdown(true); // Force channel to shutdown clean up resources.

		out("testUpdate1 - done");
	}
	
	private static void setTLSProps() throws Exception {
		// Use the appropriate TLS/non-TLS network config file
		networkConfig = NetworkConfig.fromYamlFile(CONFIG.getNetworkConfigFile());

		out("设置Orderer证书配置");
		networkConfig.getOrdererNames().forEach(ordererName -> {
			try {
				Properties ordererProps = networkConfig.getOrdererProperties(ordererName);

				Properties ordererTLSProp = CONFIG.getTLSCertProperties("orderer", ordererName);

				ordererProps.setProperty("clientCertFile", ordererTLSProp.getProperty("clientCertFile"));
				ordererProps.setProperty("clientKeyFile", ordererTLSProp.getProperty("clientKeyFile"));

				networkConfig.setOrdererProperties(ordererName, ordererProps);
			} catch (InvalidArgumentException e) {
				throw new RuntimeException(e);
			}
		});

		out("设置Peer证书配置");
		networkConfig.getPeerNames().forEach(peerName -> {
			try {
				Properties peerProps = networkConfig.getPeerProperties(peerName);

				Properties peerTLSProp = CONFIG.getTLSCertProperties("peer", peerName);
				peerProps.setProperty("clientCertFile", peerTLSProp.getProperty("clientCertFile"));
				peerProps.setProperty("clientKeyFile", peerTLSProp.getProperty("clientKeyFile"));

				networkConfig.setPeerProperties(peerName, peerProps);
			} catch (InvalidArgumentException e) {
				throw new RuntimeException(e);
			}
		});

		out("设置EventHub证书配置");
		networkConfig.getEventHubNames().forEach(eventhubName -> {
			try {
				Properties eventHubsProps = networkConfig.getEventHubsProperties(eventhubName);

				Properties peerProp = CONFIG.getTLSCertProperties("peer", eventhubName);
				eventHubsProps.setProperty("clientCertFile", peerProp.getProperty("clientCertFile"));
				eventHubsProps.setProperty("clientKeyFile", peerProp.getProperty("clientKeyFile"));

				networkConfig.setEventHubProperties(eventhubName, eventHubsProps);
			} catch (InvalidArgumentException e) {
				throw new RuntimeException(e);
			}
		});
	}
	
	private static void checkCaClient(String orgName) throws Exception {
		out("检查我们是否可以访问定义的CA");
		
		NetworkConfig.OrgInfo org = networkConfig.getOrganizationInfo(orgName);
		CAInfo caInfo = org.getCertificateAuthorities().get(0);

		// 创建CA客户端
		HFCAClient caClient = HFCAClient.createNewInstance(caInfo);
		assertEquals("CA 名称不一致", caClient.getCAName(), caInfo.getCAName());
		
		HFCAInfo info = caClient.info(); // makes actual REST call.
		out("caInfo.caName: %s, info.caName: %s", caInfo.getCAName(), info.getCAName());
		if (caInfo.getCAName() == null) {
			assertTrue("Info CA 名称不一致", StringUtils.equals(info.getCAName(), ""));
		} else {
			assertTrue("Info CA 名称不一致", StringUtils.equals(caInfo.getCAName(), info.getCAName()));
		}
		
		registrarUsers(caClient, caInfo, org);
	}
	
	private static void registrarUsers(HFCAClient caClient, CAInfo caInfo, NetworkConfig.OrgInfo org) throws Exception {
		out("在 %s/%s 注册新用户", org.getName(), org.getMspId());
		
		// 获取注册的用户
		Collection<UserInfo> registrars = caInfo.getRegistrars();
		assertTrue("没有注册用户", !registrars.isEmpty());
		
		UserInfo registrar = registrars.iterator().next();
		registrar.setEnrollment(caClient.enroll(registrar.getName(), registrar.getEnrollSecret())); // 进行认证
		
		MockUser mockuser = TestUtils.getMockUser(org.getName() + "_mock_" + System.nanoTime(), registrar.getMspId());
		out("注册用户：%s", mockuser.getName());
		
		// 注册一个新用户，并认证
		RegistrationRequest registrationRequest = new RegistrationRequest(mockuser.getName(), "org1.department1");
		mockuser.setEnrollmentSecret(caClient.register(registrationRequest, registrar)); // 注册
		mockuser.setEnrollment(caClient.enroll(mockuser.getName(), mockuser.getEnrollmentSecret())); // 认证
		
		ORG_REGISTERED_USERS.put(org.getName(), mockuser); // 添加到缓存
	}

	// 确定链码是否已部署，如果未部署时就进行部署
	private static void deployChaincodeIfRequired() throws Exception {
		out("确定链码是否已部署，如果未部署时就进行部署");
		
		// Setup client
		HFClient client = getClient();

		Channel channel = constructChannel(client, FOO_CHANNEL_NAME);

		// Use any old peer...
		Peer peer = channel.getPeers().iterator().next();
		if (!checkInstantiatedChaincode(channel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {

			// The chaincode we require does not exist, so deploy it...
			deployChaincode(client, channel, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION);
		}
	}

	// Returns a new client instance
	private static HFClient getClient() throws Exception {
		out("创建HF Client");
		
		HFClient client = HFClient.createNewInstance();
		client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

		User peerAdmin = getAdminUser(ORG_NAME);
		client.setUserContext(peerAdmin);

		return client;
	}

	private static User getAdminUser(String orgName) throws Exception {
		out("从 networkConfig 配置 %s 获取 PeerAdmin", orgName);
		
		return networkConfig.getPeerAdmin(orgName);
	}

	private static void queryChaincodeForExpectedValue(HFClient client, Channel channel, final String expect, ChaincodeID chaincodeID) {
		out("查询 chaincode: %s 在 channel： %s 上查询账户  b 余额：%s", chaincodeID, channel.getName(), expect);

		String value = queryChaincodeForCurrentValue(client, channel, chaincodeID);
		assertEquals(expect, value);
	}

	// Returns the current value of b's assets
	private static String queryChaincodeForCurrentValue(HFClient client, Channel channel, ChaincodeID chaincodeID) {
		out("查询 chaincode: %s 在 channel： %s 上查询账户  b 余额", chaincodeID, channel.getName());

		out("create query chaincode request");
		QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
		queryByChaincodeRequest.setArgs("b");
		queryByChaincodeRequest.setFcn("query");
		queryByChaincodeRequest.setChaincodeID(chaincodeID);

		Collection<ProposalResponse> queryProposals;
		try {
			queryProposals = channel.queryByChaincode(queryByChaincodeRequest);
		} catch (Exception e) {
			throw new CompletionException(e);
		}

		String expect = null;
		for (ProposalResponse proposalResponse : queryProposals) {
			if (!proposalResponse.isVerified() || proposalResponse.getStatus() != Status.SUCCESS) {
				fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() + ". Messages: " + proposalResponse.getMessage() + ". Was verified : " + proposalResponse.isVerified());
			} else {
				String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
				out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
				if (expect != null) {
					assertEquals(expect, payload);
				} else {
					expect = payload;
				}
			}
		}
		return expect;
	}

	private static CompletableFuture<BlockEvent.TransactionEvent> transferAmount(HFClient client, Channel channel, ChaincodeID chaincodeID, String from, String to, String moveAmount, User user) throws Exception {
		out("用户: %s 在通道 %s 调用ChainCode %s，从 %s 向 %s 发起转账交易金额：%s", user == null ? client.getUserContext().getName() : user.getName(), channel.getName(), chaincodeID, from, to, moveAmount);
		
		Collection<ProposalResponse> successful = new LinkedList<>();
		Collection<ProposalResponse> failed = new LinkedList<>();

		out("create transaction proposal request");
		TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
		transactionProposalRequest.setChaincodeID(chaincodeID);
		transactionProposalRequest.setFcn("move");
		transactionProposalRequest.setArgs(from, to, moveAmount);
		transactionProposalRequest.setProposalWaitTime(CONFIG.getProposalWaitTime());
		
		if (user != null) { // specific user use that
			transactionProposalRequest.setUserContext(user);
		}
		
		/// Send transaction proposal to all peers
		Collection<ProposalResponse> invokePropResp = channel.sendTransactionProposal(transactionProposalRequest);
		out("sending transaction proposal to all peers with arguments: move(%s,%s,%s)", from, to, moveAmount);

		for (ProposalResponse response : invokePropResp) {
			if (response.getStatus() == Status.SUCCESS) {
				out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				successful.add(response);
			} else {
				out("failed transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				failed.add(response);
			}
		}

		// Check that all the proposals are consistent with each other. We should have only one set
		// where all the proposals above are consistent.
		Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(invokePropResp);
		if (proposalConsistencySets.size() != 1) {
			fail(format("Expected only one set of consistent move proposal responses but got %d", proposalConsistencySets.size()));
		}

		out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d", invokePropResp.size(), successful.size(), failed.size());
		if (failed.size() > 0) {
			ProposalResponse firstTransactionProposalResponse = failed.iterator().next();

			throw new ProposalException(format(
					"Not enough endorsers for invoke(move %s,%s,%s):%d endorser error:%s. Was verified:%b", from, to,
					moveAmount, firstTransactionProposalResponse.getStatus().getStatus(),
					firstTransactionProposalResponse.getMessage(), firstTransactionProposalResponse.isVerified()));
		}
		out("Successfully received transaction proposal responses.");

		////////////////////////////
		// Send transaction to orderer
		out("Sending chaincode transaction(move %s,%s,%s) to orderer.", from, to, moveAmount);
		if (user != null) {
			return channel.sendTransaction(successful, user);
		}

		return channel.sendTransaction(successful);
	}

	private static ChaincodeID deployChaincode(HFClient client, Channel channel, String ccName, String ccPath, String ccVersion) throws Exception {
		ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(ccName).setVersion(ccVersion).setPath(ccPath).build();
		out("部署Chaincode：%s", chaincodeID);

		try {
			final String channelName = channel.getName();
			out("deployChaincode - channelName = " + channelName);

			Collection<Orderer> orderers = channel.getOrderers();
			Collection<ProposalResponse> responses;
			
			Collection<ProposalResponse> successful = new LinkedList<>();
			Collection<ProposalResponse> failed = new LinkedList<>();


			//-----------------------------------------------------------------------
			// Install Proposal Request
			out("Creating install proposal");

			InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
			installProposalRequest.setChaincodeID(chaincodeID);

			//// For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
			installProposalRequest.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH + "/sdkintegration/gocc/sample1"));
			installProposalRequest.setChaincodeVersion(ccVersion);

			////////////////////////////
			// only a client from the same org as the peer can issue an install request
			out("Sending install proposal");
			int numInstallProposal = 0;

			Collection<Peer> peers = channel.getPeers();
			numInstallProposal = numInstallProposal + peers.size();
			responses = client.sendInstallProposal(installProposalRequest, peers);
			
			out("发出安装请求：%s", installProposalRequest.getChaincodeSourceLocation().getAbsolutePath());

			for (ProposalResponse response : responses) {
				if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
					out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
					successful.add(response);
				} else {
					failed.add(response);
					out("failed install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				}
			}

			out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

			if (failed.size() > 0) {
				ProposalResponse first = failed.iterator().next();
				fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
			}

			//-----------------------------------------------------------------------
			//// Instantiate chaincode.
			//
			// From the docs:
			// The instantiate transaction invokes the lifecycle System Chaincode (LSCC) to create and initialize a
			/////////////// chaincode on a channel
			// After being successfully instantiated, the chaincode enters the active state on the channel and is ready
			/////////////// to process any transaction proposals of type ENDORSER_TRANSACTION

			out("Creating instantiate proposal");
			InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
			instantiateProposalRequest.setProposalWaitTime(CONFIG.getProposalWaitTime());
			instantiateProposalRequest.setChaincodeID(chaincodeID);
			instantiateProposalRequest.setFcn("init");
			instantiateProposalRequest.setArgs("a", "500", "b", "999");

			Map<String, byte[]> tm = new HashMap<>();
			tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
			tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
			
			instantiateProposalRequest.setTransientMap(tm);

			/*
			 * policy OR(Org1MSP.member, Org2MSP.member) 
			 * meaning 1 signature from someone in either Org1 or Org2 See
			 * README.md Chaincode endorsement policies section for more details.
			 */
			ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
			chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
			instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

			out("Sending instantiateProposalRequest to all peers...");
			responses = channel.sendInstantiationProposal(instantiateProposalRequest);

			successful.clear();
			failed.clear();
			for (ProposalResponse response : responses) {
				if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
					successful.add(response);
					out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				} else {
					failed.add(response);
					out("failed instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				}
			}
			out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
			if (failed.size() > 0) {
				ProposalResponse first = failed.iterator().next();
				fail("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
			}

			///////////////
			/// Send instantiate transaction to orderer
			out("Sending instantiate Transaction to orderer...");
			CompletableFuture<TransactionEvent> future = channel.sendTransaction(successful, orderers);

			out("calling get...");
			TransactionEvent event = future.get(30, TimeUnit.SECONDS);
			out("get done...");

			assertTrue(event.isValid()); // must be valid to be here.
			out("channelId: %s, validCode: %s, txId: %s, type: %s", event.getChannelId(), event.getValidationCode(), event.getTransactionID(), event.getType());
			
			out("Finished instantiate transaction with transaction id %s", event.getTransactionID());
		} catch (Exception e) {
			e.printStackTrace();
			out("Caught an exception running channel %s", channel.getName());
			fail("Test failed with error : " + e.getMessage());
		}

		return chaincodeID;
	}

	private static Channel constructChannel(HFClient client, String channelName) throws Exception {
		out("从networkConfig配置中加载通道: %s", channelName);

		// Channel newChannel = client.getChannel(channelName);
		Channel newChannel = client.loadChannelFromConfig(channelName, networkConfig);
		if (newChannel == null) {
			throw new RuntimeException("Channel " + channelName + " is not defined in the config file!");
		}

		return newChannel.initialize();
	}

	// Determines if the specified chaincode has been instantiated on the channel
	private static boolean checkInstantiatedChaincode(Channel channel, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
		out("检查是否有实例化chaincode: %s, at version: %s, on peer: %s", ccName, ccVersion, peer.getName());
		
		List<ChaincodeInfo> ccinfoList = channel.queryInstantiatedChaincodes(peer);

		boolean found = false;
		for (ChaincodeInfo ccifo : ccinfoList) {
			found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
			if (found) {
				break;
			}
		}

		return found;
	}

	private static void out(String format, Object... args) {

		System.err.flush();
		System.out.flush();

		System.out.println(format(format, args));
		System.err.flush();
		System.out.flush();
	}
}
