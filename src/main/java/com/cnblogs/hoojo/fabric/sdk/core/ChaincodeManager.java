package com.cnblogs.hoojo.fabric.sdk.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.protos.peer.Query.ChaincodeInfo;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.UpgradeProposalRequest;
import org.hyperledger.fabric.sdk.User;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;
import com.cnblogs.hoojo.fabric.sdk.util.Util;
import com.google.common.base.Optional;

/**
 * <b>function:</b> chaincode 管理服务，包括 install、upgrade、instantiate
 * 
 * @author hoojo
 * @createDate 2018年6月22日 下午4:51:01
 * @file ChaincodeManager.java
 * @package com.cnblogs.hoojo.fabric.sdk.core
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class ChaincodeManager extends ApplicationLogging {

	private HFClient client;
	private Channel channel;
	private DefaultConfiguration config;

	public ChaincodeManager(HFClient client, Channel channel, DefaultConfiguration config) {
		this.client = client;
		this.channel = channel;
		this.config = config;
	}

	/**
	 * 安装Chaincode智能合约
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(ChaincodeID chaincodeId, Type language, boolean streamed) throws Exception {

		File chaincodeSourceFile = null;
		InputStream chaincodeStream = null;

		if (!streamed) { // foo cc 直接从gopath目录下安装
			chaincodeSourceFile = Paths.get(config.getRootPath(), config.getChaincodePath()).toFile();
			logger.debug("Foo-Chaincode path: {}", chaincodeSourceFile.getAbsolutePath());
		} else {
			if (language.equals(Type.GO_LANG)) {
				File chaincodeFile = Paths
						.get(config.getRootPath(), config.getChaincodePath(), "src", chaincodeId.getPath()).toFile();
				logger.debug("Chaincode path: {}", chaincodeFile.getAbsolutePath());
				chaincodeStream = Util.generateTarGzInputStream(chaincodeFile,
						Paths.get("src", chaincodeId.getPath()).toString());
			} else {
				File chaincodeFile = Paths.get(config.getRootPath(), config.getChaincodePath()).toFile();
				logger.debug("Chaincode path: {}", chaincodeFile.getAbsolutePath());
				chaincodeStream = Util.generateTarGzInputStream(chaincodeFile, "src");
			}
		}

		installChaincode(chaincodeId, language, chaincodeSourceFile, chaincodeStream);
	}

	/**
	 * 通过chaincode文件，安装Chaincode智能合约
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(ChaincodeID chaincodeId, Type language, File chaincodeSourceFile) throws Exception {
		checkNotNull(chaincodeSourceFile, "chaincodeSourceFile 是必填参数");

		installChaincode(chaincodeId, language, chaincodeSourceFile, null);
	}

	/**
	 * 通过chaincode文件流，安装Chaincode智能合约
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(ChaincodeID chaincodeId, Type language, InputStream chaincodeStream) throws Exception {
		checkNotNull(chaincodeStream, "chaincodeStream 是必填参数");

		installChaincode(chaincodeId, language, null, chaincodeStream);
	}

	/**
	 * 安装Chaincode智能合约
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	private void installChaincode(ChaincodeID chaincodeId, Type language, File chaincodeSourceFile, InputStream chaincodeStream) throws Exception {
		logger.info("通道：{} 安装chaincode: {}", channel.getName(), chaincodeId);

		checkNotNull(chaincodeId, "chaincodeId 是必填参数");
		checkNotNull(language, "language 是必填参数");

		Collection<ProposalResponse> successful = new LinkedList<>();
		Collection<ProposalResponse> failed = new LinkedList<>();

		/***************** 构建安装chaincode请求 *******************/
		InstallProposalRequest installRequest = client.newInstallProposalRequest();
		installRequest.setChaincodeID(chaincodeId);
		installRequest.setChaincodeVersion(chaincodeId.getVersion());
		installRequest.setChaincodeLanguage(language);

		if (chaincodeSourceFile != null) { // foo cc 直接从gopath目录下安装
			logger.debug("ChaincodeSourceFile path: {}", chaincodeSourceFile.getAbsolutePath());
			installRequest.setChaincodeSourceLocation(chaincodeSourceFile);
		} else {
			installRequest.setChaincodeInputStream(chaincodeStream);
		}

		/************************ 发送安装请求 ***************************/
		// 只有来自同一组织的客户端才能发出安装请求
		Collection<Peer> peers = channel.getPeers();
		Collection<ProposalResponse> responses = client.sendInstallProposal(installRequest, peers);
		logger.info("向channel.Peers节点——发送安装chaincode请求：{}", installRequest);

		for (ProposalResponse response : responses) {
			if (response.getStatus() == Status.SUCCESS) {
				successful.add(response);
				logger.debug("成功安装 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			} else {
				failed.add(response);
				logger.debug("失败安装 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			}
		}

		logger.info("接收安装请求数量： {}， 成功安装并验证通过数量: {} . 失败数量: {}", peers.size(), successful.size(), failed.size());
		if (failed.size() > 0) {
			ProposalResponse first = failed.iterator().next();
			throw new RuntimeException("没有足够的 endorsers 安装 :" + successful.size() + "，  " + first.getMessage());
		}
	}

	/**
	 * 实例化Chaincode
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	@SuppressWarnings("unused")
	private Collection<ProposalResponse> instantiateChaincode(String endorsementPolicyYamlFilePath, ChaincodeID chaincodeId, Type language, String func, String... args) throws Exception {

		return instantiateChaincode(getChaincodeEndorsementPolicy(endorsementPolicyYamlFilePath), chaincodeId, language, func, args);
	}

	/**
	 * 实例化Chaincode
	 * 
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	@SuppressWarnings("unused")
	public Collection<ProposalResponse> instantiateChaincode(ChaincodeEndorsementPolicy endorsementPolicy, ChaincodeID chaincodeId, Type language, String func, String... args) throws Exception {
		logger.info("在通道：{} 实例化Chaincode：{}", channel.getName(), chaincodeId);

		checkNotNull(endorsementPolicy, "endorsementPolicy 背书策略文件为必填项");

		func = Optional.fromNullable(func).or("init");
		args = Optional.fromNullable(args).or(new String[] {});

		Collection<ProposalResponse> successful = new LinkedList<>();
		Collection<ProposalResponse> failed = new LinkedList<>();

		// 注意安装chaincode不需要事务不需要发送给 Orderers
		InstantiateProposalRequest instantiateRequest = client.newInstantiationProposalRequest();
		instantiateRequest.setChaincodeID(chaincodeId);
		instantiateRequest.setChaincodeVersion(chaincodeId.getVersion());
		instantiateRequest.setChaincodeLanguage(language);
		instantiateRequest.setProposalWaitTime(config.getProposalWaitTime());
		instantiateRequest.setFcn(func);
		instantiateRequest.setArgs(args);

		Map<String, byte[]> data = new HashMap<>();
		data.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
		data.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
		instantiateRequest.setTransientMap(data);

		// 设置背书策略
		instantiateRequest.setChaincodeEndorsementPolicy(endorsementPolicy);

		// 通过指定对等节点和使用通道上的方式发送请求响应
		Collection<ProposalResponse> responses = null;
		if (true) {
			responses = channel.sendInstantiationProposal(instantiateRequest, channel.getPeers());
			logger.info("向channel.Peers节点——发送实例化Chaincode请求：{}", instantiateRequest);
		} else {
			responses = channel.sendInstantiationProposal(instantiateRequest);
			logger.info("向CHAINCODE_QUERY/ENDORSING_PEER Peer——发送实例化Chaincode请求：{}", instantiateRequest);
		}
		logger.info("发送实例化Chaincode参数：{}", instantiateRequest.getArgs());

		for (ProposalResponse response : responses) {
			if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
				successful.add(response);
				logger.debug("成功实例化 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			} else {
				failed.add(response);
				logger.debug("失败实例化 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			}
		}
		logger.info("接收实例化请求数量： {}， 成功安装并验证通过数量: {}， 失败数量: {}", responses.size(), successful.size(), failed.size());

		if (failed.size() > 0) {
			for (ProposalResponse fail : failed) {
				logger.error("没有足够的 endorsers 实例化:" + successful.size() + "，endorser failed： " + fail.getMessage() + ", peer：" + fail.getPeer());
			}

			ProposalResponse first = failed.iterator().next();
			throw new RuntimeException("没有足够的 endorsers 实例化:" + successful.size() + "，endorser failed： " + first.getMessage() + ", verified：" + first.isVerified());
		}

		return successful;
	}

	/**
	 * 升级Chaincode
	 * 
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:50:11
	 */
	public TransactionEvent upgradeChaincode(String endorsementPolicyYamlFilePath, ChaincodeID chaincodeId, User userCtx, String func, String... args) throws Exception {

		return upgradeChaincode(getChaincodeEndorsementPolicy(endorsementPolicyYamlFilePath), chaincodeId, userCtx, func, args);
	}

	private ChaincodeEndorsementPolicy getChaincodeEndorsementPolicy(String endorsementPolicyYamlFilePath) throws Exception {
		File policyFile = Paths.get(config.getRootPath(), endorsementPolicyYamlFilePath).toFile();
		logger.info("背书策略文件：{}", policyFile.getAbsolutePath());

		ChaincodeEndorsementPolicy endorsementPolicy = new ChaincodeEndorsementPolicy();
		endorsementPolicy.fromYamlFile(policyFile);

		return endorsementPolicy;
	}

	/**
	 * 升级Chaincode
	 * 
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:50:11
	 */
	public TransactionEvent upgradeChaincode(ChaincodeEndorsementPolicy endorsementPolicy, ChaincodeID chaincodeId, User userCtx, String func, String... args) throws Exception {
		logger.info("通道：{} 升级安装 chaincode: {}", channel.getName(), chaincodeId);

		checkNotNull(endorsementPolicy, "endorsementPolicy 背书策略文件为必填项");

		func = Optional.fromNullable(func).or("init");
		args = Optional.fromNullable(args).or(new String[] {});

		UpgradeProposalRequest upgradeProposalRequest = client.newUpgradeProposalRequest();
		upgradeProposalRequest.setChaincodeID(chaincodeId);
		upgradeProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
		upgradeProposalRequest.setFcn(func);
		upgradeProposalRequest.setArgs(args); // no arguments don't change the ledger see chaincode.

		// 设置背书策略
		upgradeProposalRequest.setChaincodeEndorsementPolicy(endorsementPolicy);

		Map<String, byte[]> tmap = new HashMap<>();
		tmap.put("test", "data".getBytes());
		upgradeProposalRequest.setTransientMap(tmap);

		if (userCtx != null) {
			// org.getPeerAdmin()
			upgradeProposalRequest.setUserContext(userCtx);
		}

		final Collection<ProposalResponse> successful = new LinkedList<>();
		final Collection<ProposalResponse> failed = new LinkedList<>();

		// 发送安装升级chaincode请求
		Collection<ProposalResponse> responses = channel.sendUpgradeProposal(upgradeProposalRequest);
		logger.debug("向channel节点——发送安装升级chaincode请求：{}", upgradeProposalRequest);

		for (ProposalResponse response : responses) {
			if (response.getStatus() == Status.SUCCESS) {
				successful.add(response);
				logger.debug("成功升级 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			} else {
				failed.add(response);
				logger.debug("失败升级 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			}
		}

		logger.debug("接收安装请求数量： {}， 成功安装并验证通过数量: {} . 失败数量: {}", channel.getPeers().size(), successful.size(), failed.size());
		if (failed.size() > 0) {
			ProposalResponse first = failed.iterator().next();
			throw new RuntimeException("没有足够的 endorsers 安装 :" + successful.size() + "，  " + first.getMessage());
		}

		logger.info("向Orderer节点——发起 执行Chaincode 升级");
		if (userCtx != null) {
			return channel.sendTransaction(successful, userCtx).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
		} else {
			return channel.sendTransaction(successful).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
		}
	}

	/**
	 * 检查Chaincode在peer上是否成功安装
	 * 
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:49:01
	 */
	public boolean checkInstalledChaincode(Peer peer, ChaincodeID chaincodeId) throws Exception {
		logger.info("检查是否存在chaincode: {}, version: {}, peer: {}", chaincodeId.getName(), chaincodeId.getVersion(), peer.getName());

		List<ChaincodeInfo> list = client.queryInstalledChaincodes(peer);

		boolean found = false;
		for (ChaincodeInfo chaincodeInfo : list) {
			logger.debug("Peer: {} 已安装chaincode：{}", peer.getName(), chaincodeInfo);

			if (chaincodeId.getPath() != null) {
				found = chaincodeId.getName().equals(chaincodeInfo.getName()) && chaincodeId.getPath().equals(chaincodeInfo.getPath()) && chaincodeId.getVersion().equals(chaincodeInfo.getVersion());
				if (found) {
					break;
				}
			}

			found = chaincodeId.getName().equals(chaincodeInfo.getName()) && chaincodeId.getVersion().equals(chaincodeInfo.getVersion());
			if (found) {
				break;
			}
		}

		return found;
	}

	/**
	 * 检查Chaincode在channel上是否实例化
	 * 
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:48:29
	 */
	public boolean checkInstantiatedChaincode(Channel channel, Peer peer, ChaincodeID chaincodeId) throws Exception {
		logger.info("在通道：{} 检查是否实例化 chaincode: {}, version: {}, peer: {}", channel.getName(), chaincodeId.getName(), chaincodeId.getVersion(), peer.getName());

		List<ChaincodeInfo> chaincodeList = channel.queryInstantiatedChaincodes(peer);

		boolean found = false;
		for (ChaincodeInfo chaincodeInfo : chaincodeList) {
			logger.debug("已实例化 chaincode：{}", chaincodeInfo);

			if (chaincodeId.getPath() != null) {
				found = chaincodeId.getName().equals(chaincodeInfo.getName())
						&& chaincodeId.getPath().equals(chaincodeInfo.getPath())
						&& chaincodeId.getVersion().equals(chaincodeInfo.getVersion());
				if (found) {
					break;
				}
			}

			found = chaincodeId.getName().equals(chaincodeInfo.getName()) && chaincodeId.getVersion().equals(chaincodeInfo.getVersion());
			if (found) {
				break;
			}
		}

		return found;
	}

	/**
	 * 检查Chaincode是否在通道上成功安装和实例化
	 * 
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:47:40
	 */
	public boolean checkChaincode(Channel channel, Organization org, ChaincodeID chaincodeId) throws Exception {
		logger.info("检查通道 {} 是否安装且实例化Chaincode： {}", channel.getName(), chaincodeId.toString());

		// 设置对等节点用户上下文
		client.setUserContext(org.getPeerAdmin());

		for (Peer peer : channel.getPeers()) {
			if (checkInstalledChaincode(peer, chaincodeId) && checkInstantiatedChaincode(channel, peer, chaincodeId)) {
				return true;
			}
		}

		return false;
	}
}
