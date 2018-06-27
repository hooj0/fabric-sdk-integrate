package com.cnblogs.hoojo.fabric.sdk.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
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
import com.cnblogs.hoojo.fabric.sdk.entity.InstallEntity;
import com.cnblogs.hoojo.fabric.sdk.entity.InstantiateUpgradeEntity;
import com.cnblogs.hoojo.fabric.sdk.util.GzipUtils;
import com.google.common.base.Optional;

/**
 * chaincode 管理服务，包括 install、upgrade、instantiate
 * @author hoojo
 * @createDate 2018年6月22日 下午4:51:01
 * @file ChaincodeManager.java
 * @package com.cnblogs.hoojo.fabric.sdk.core
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class ChaincodeManager extends AbstractTransactionManager {

	public ChaincodeManager(DefaultConfiguration config, HFClient client) {
		super(config, client);
	}

	/**
	 * 安装Chaincode智能合约
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(Channel channel, InstallEntity chaincode, boolean streamed) throws Exception {

		if (!streamed) { 
			installChaincode(channel, chaincode, Paths.get(config.getChaincodePath()).toFile());
			
			logger.debug("Chaincode path: {}", chaincode.getChaincodeSourceFile().getAbsolutePath());
		} else {
			if (chaincode.getLanguage() == Type.GO_LANG) {
				File chaincodeFile = Paths.get(config.getChaincodePath(), "src", chaincode.getChaincodeId().getPath()).toFile();
				logger.debug("Chaincode path: {}", chaincodeFile.getAbsolutePath());
				
				installChaincode(channel, chaincode, GzipUtils.generateTarGzInputStream(chaincodeFile, Paths.get("src", chaincode.getChaincodeId().getPath()).toString()));
			} else {
				File chaincodeFile = Paths.get(config.getChaincodePath()).toFile();
				logger.debug("Chaincode path: {}", chaincodeFile.getAbsolutePath());
				
				installChaincode(channel, chaincode, GzipUtils.generateTarGzInputStream(chaincodeFile, "src"));
			}
		}
	}

	/**
	 * 通过chaincode文件，安装Chaincode智能合约
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(Channel channel, InstallEntity chaincode, File chaincodeSourceFile) throws Exception {
		chaincode.setChaincodeSourceFile(checkNotNull(chaincodeSourceFile, "chaincodeSourceFile 是必填参数"));
		chaincode.setChaincodeSourceStream(null);
		
		installChaincode(channel, chaincode);
	}

	/**
	 * 通过chaincode文件流，安装Chaincode智能合约
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(Channel channel, InstallEntity chaincode, InputStream chaincodeStream) throws Exception {
		chaincode.setChaincodeSourceStream(checkNotNull(chaincodeStream, "chaincodeStream 是必填参数"));
		chaincode.setChaincodeSourceFile(null);

		installChaincode(channel, chaincode);
	}

	/**
	 * 安装Chaincode智能合约
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:52:27
	 */
	public void installChaincode(Channel channel, InstallEntity chaincode) throws Exception {
		logger.info("通道：{} 安装chaincode: {}", channel.getName(), chaincode.getChaincodeId());

		checkNotNull(chaincode.getChaincodeId(), "chaincodeId 是必填参数");
		checkNotNull(chaincode.getLanguage(), "language 是必填参数");

		Collection<ProposalResponse> successful = new LinkedList<>();
		Collection<ProposalResponse> failed = new LinkedList<>();

		// 构建安装chaincode请求
		InstallProposalRequest installRequest = client.newInstallProposalRequest();
		installRequest.setProposalWaitTime(config.getProposalWaitTime());
		installRequest.setChaincodeLanguage(chaincode.getLanguage());
		installRequest.setChaincodeID(chaincode.getChaincodeId());
		
		if (!StringUtils.isBlank(chaincode.getChaincodeVersion())) {
			installRequest.setChaincodeVersion(chaincode.getChaincodeVersion());
		}
		if (chaincode.getChaincodeSourceFile() != null) { 
			logger.debug("ChaincodeSourceFile path: {}", chaincode.getChaincodeSourceFile().getAbsolutePath());
			installRequest.setChaincodeSourceLocation(chaincode.getChaincodeSourceFile());
		} else {
			installRequest.setChaincodeInputStream(chaincode.getChaincodeSourceStream());
		}

		// 发送安装请求
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
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public Collection<ProposalResponse> instantiateChaincode(Channel channel, InstantiateUpgradeEntity chaincode) throws Exception {
		return instantiateChaincode(channel, chaincode, null);
	}
	
	/**
	 * 实例化Chaincode
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public Collection<ProposalResponse> instantiateChaincode(Channel channel, InstantiateUpgradeEntity chaincode, User reqUserCtx) throws Exception {
		logger.info("在通道：{} 实例化Chaincode：{}", channel.getName(), chaincode.getChaincodeId());

		checkNotNull(chaincode.getEndorsementPolicy(), "endorsementPolicy 背书策略文件为必填项");

		chaincode.setFunc(Optional.fromNullable(chaincode.getFunc()).or("init"));
		chaincode.setArgs(Optional.fromNullable(chaincode.getArgs()).or(new String[] {}));

		// 注意安装chaincode不需要事务不需要发送给 Orderers
		InstantiateProposalRequest instantiateRequest = client.newInstantiationProposalRequest();
		instantiateRequest.setChaincodeLanguage(chaincode.getLanguage());
		instantiateRequest.setChaincodeID(chaincode.getChaincodeId());
		instantiateRequest.setFcn(chaincode.getFunc());
		instantiateRequest.setArgs(chaincode.getArgs());
		instantiateRequest.setProposalWaitTime(config.getProposalWaitTime());

		Map<String, byte[]> transientMap = new HashMap<>();
		transientMap.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
		transientMap.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
		
		if (chaincode.getTransientMap() != null) {
			transientMap.putAll(chaincode.getTransientMap());
		}
		instantiateRequest.setTransientMap(transientMap);

		// 设置背书策略
		instantiateRequest.setChaincodeEndorsementPolicy(chaincode.getEndorsementPolicy());
		
		if (reqUserCtx != null) {
			instantiateRequest.setUserContext(reqUserCtx);
		}

		// 通过指定对等节点和使用通道上的方式发送请求响应
		Collection<ProposalResponse> responses = null;
		if (chaincode.isSpecificPeers()) {
			responses = channel.sendInstantiationProposal(instantiateRequest, channel.getPeers());
			logger.info("向channel.Peers节点——发送实例化Chaincode请求：{}", instantiateRequest);
		} else {
			responses = channel.sendInstantiationProposal(instantiateRequest);
			logger.info("向CHAINCODE_QUERY/ENDORSING_PEER Peer——发送实例化Chaincode请求：{}", instantiateRequest);
		}
		logger.info("发送实例化Chaincode参数：{}", instantiateRequest.getArgs());

		Collection<ProposalResponse> successResponses = new LinkedList<>();
		Collection<ProposalResponse> failedResponses = new LinkedList<>();
		
		for (ProposalResponse response : responses) {
			if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
				successResponses.add(response);
				logger.debug("成功实例化 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			} else {
				failedResponses.add(response);
				logger.debug("失败实例化 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			}
		}
		logger.info("接收实例化请求数量： {}， 成功安装并验证通过数量: {}， 失败数量: {}", responses.size(), successResponses.size(), failedResponses.size());

		if (failedResponses.size() > 0) {
			for (ProposalResponse fail : failedResponses) {
				logger.error("没有足够的 endorsers 实例化:" + successResponses.size() + "，endorser failed： " + fail.getMessage() + ", peer：" + fail.getPeer());
			}

			ProposalResponse first = failedResponses.iterator().next();
			throw new RuntimeException("没有足够的 endorsers 实例化:" + successResponses.size() + "，endorser failed： " + first.getMessage() + ", verified：" + first.isVerified());
		}

		return successResponses;
	}

	/**
	 * 升级Chaincode
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:50:11
	 */
	public Collection<ProposalResponse> upgradeChaincode(Channel channel, InstantiateUpgradeEntity chaincode) throws Exception {
		return upgradeChaincode(channel, chaincode, null);
	}
	
	/**
	 * 升级Chaincode
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:50:11
	 */
	public Collection<ProposalResponse> upgradeChaincode(Channel channel, InstantiateUpgradeEntity chaincode, User reqUserCtx) throws Exception {
		logger.info("通道：{} 升级安装 chaincode: {}", channel.getName(), chaincode.getChaincodeId());

		checkNotNull(chaincode.getEndorsementPolicy(), "endorsementPolicy 背书策略文件为必填项");

		chaincode.setFunc(Optional.fromNullable(chaincode.getFunc()).or("init"));
		chaincode.setArgs(Optional.fromNullable(chaincode.getArgs()).or(new String[] {}));

		UpgradeProposalRequest upgradeProposalRequest = client.newUpgradeProposalRequest();
		upgradeProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
		upgradeProposalRequest.setChaincodeLanguage(chaincode.getLanguage());
		upgradeProposalRequest.setChaincodeID(chaincode.getChaincodeId());
		upgradeProposalRequest.setFcn(chaincode.getFunc());
		upgradeProposalRequest.setArgs(chaincode.getArgs()); // no arguments don't change the ledger see chaincode.

		// 设置背书策略
		upgradeProposalRequest.setChaincodeEndorsementPolicy(chaincode.getEndorsementPolicy());

		if (chaincode.getTransientMap() != null) {
			upgradeProposalRequest.setTransientMap(chaincode.getTransientMap());
		}
		if (reqUserCtx != null) {
			upgradeProposalRequest.setUserContext(reqUserCtx);
		}

		// 发送安装升级chaincode请求
		Collection<ProposalResponse> responses = null;
		if (chaincode.isSpecificPeers()) {
			responses = channel.sendUpgradeProposal(upgradeProposalRequest, channel.getPeers());
		} else {
			responses = channel.sendUpgradeProposal(upgradeProposalRequest); // default
		}
		logger.debug("向channel节点——发送安装升级chaincode请求：{}", upgradeProposalRequest);

		final Collection<ProposalResponse> successResponses = new LinkedList<>();
		final Collection<ProposalResponse> failedResponses = new LinkedList<>();
		
		for (ProposalResponse response : responses) {
			if (response.getStatus() == Status.SUCCESS) {
				successResponses.add(response);
				logger.debug("成功升级 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			} else {
				failedResponses.add(response);
				logger.debug("失败升级 Txid: {} , peer: {}", response.getTransactionID(), response.getPeer().getName());
			}
		}

		logger.debug("接收安装请求数量： {}， 成功安装并验证通过数量: {} . 失败数量: {}", channel.getPeers().size(), successResponses.size(), failedResponses.size());
		if (failedResponses.size() > 0) {
			ProposalResponse first = failedResponses.iterator().next();
			throw new RuntimeException("没有足够的 endorsers 安装 :" + successResponses.size() + "，  " + first.getMessage());
		}

		return successResponses;
	}

	/**
	 * 设置背书策略配置
	 * @author hoojo
	 * @createDate 2018年6月25日 下午1:02:33
	 */
	public ChaincodeEndorsementPolicy getChaincodeEndorsementPolicy() throws Exception {
		return getChaincodeEndorsementPolicy(null);
	}
	
	/**
	 * 设置背书策略配置
	 * @author hoojo
	 * @createDate 2018年6月25日 下午1:02:33
	 */
	public ChaincodeEndorsementPolicy getChaincodeEndorsementPolicy(String endorsementPolicyYamlFilePath) throws Exception {
		
		File policyFile = null;
		if (StringUtils.isEmpty(endorsementPolicyYamlFilePath)) {
			policyFile = new File(config.getEndorsementPolicyFilePath());
		} else {
			policyFile = Paths.get(config.getCommonConfigRootPath(), endorsementPolicyYamlFilePath).toFile();
		}
		logger.info("背书策略文件：{}", policyFile.getAbsolutePath());

		ChaincodeEndorsementPolicy endorsementPolicy = new ChaincodeEndorsementPolicy();
		endorsementPolicy.fromYamlFile(policyFile);

		return endorsementPolicy;
	}
}
