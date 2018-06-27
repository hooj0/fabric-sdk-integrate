package com.cnblogs.hoojo.fabric.sdk.core;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.protos.peer.Query.ChaincodeInfo;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.entity.SendTransactionEntity;
import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;
import com.cnblogs.hoojo.fabric.sdk.model.Organization;

/**
 * 多线程交易 & 交易事件 管理抽象服务
 * @author hoojo
 * @createDate 2018年6月25日 下午5:53:30
 * @file AbstractTransactionManager.java
 * @package com.cnblogs.hoojo.fabric.sdk.core
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public abstract class AbstractTransactionManager extends ApplicationLogging {

	protected DefaultConfiguration config;
	protected HFClient client;
	
	public AbstractTransactionManager(DefaultConfiguration config, HFClient client) {
		super();
		this.config = config;
		this.client = client;
	}

	/**
	 * 执行交易初始化动作，返回交易事件
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public TransactionEvent sendTransaction(Channel channel, Collection<ProposalResponse> responses, SendTransactionEntity transaction) throws Exception {
		return sendTransaction(responses, transaction, channel).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
	}
	
	/**
	 * 执行交易初始化方法，返回多线程模型
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public CompletableFuture<TransactionEvent> sendTransaction(Collection<ProposalResponse> responses, SendTransactionEntity transaction, Channel channel) throws Exception {

		CompletableFuture<TransactionEvent> future = null;
		if (transaction.getOrderers() != null && transaction.getUser() != null) {
			future = channel.sendTransaction(responses, transaction.getOrderers(), transaction.getUser());
		} else if (transaction.getOptions() != null) {
			future = channel.sendTransaction(responses, transaction.getOptions());
		} else if (transaction.getUser() != null) {
			future = channel.sendTransaction(responses, transaction.getUser());
		} else if (transaction.getOrderers() != null) {
			future = channel.sendTransaction(responses, transaction.getOrderers());
		} else {
			future = channel.sendTransaction(responses);
		}
		
		return future;
	}
	
	/**
	 * 检查Chaincode在peer上是否成功安装
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:49:01
	 */
	public boolean checkInstallChaincode(Peer peer, ChaincodeID chaincodeId) throws Exception {
		logger.info("安装检查 —— peer: {}，chaincode: {}", peer.getName(), chaincodeId);

		List<ChaincodeInfo> list = client.queryInstalledChaincodes(peer);

		boolean found = false;
		for (ChaincodeInfo chaincodeInfo : list) {
			logger.debug("Peer: {} 已安装chaincode：{}:{}", peer.getName(), chaincodeInfo.getName(), chaincodeInfo.getVersion());

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
	 * 检查通道上的peer对等节点是否安装chaincode
	 * @author hoojo
	 * @createDate 2018年6月26日 下午5:27:28
	 */
	public boolean checkInstallChaincode(Channel channel, ChaincodeID chaincodeId) throws Exception {

		boolean found = false;
		for (Peer peer : channel.getPeers()) {
			if (checkInstallChaincode(peer, chaincodeId)) {
				found = true;
			}
		}
		
		return found;
	}

	/**
	 * 检查Chaincode在channel上是否实例化
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:48:29
	 */
	public boolean checkInstantiatedChaincode(Channel channel, Peer peer, ChaincodeID chaincodeId) throws Exception {
		logger.info("实例化检查——channel：{}, peer: {}, chaincode: {}", channel.getName(), peer.getName(), chaincodeId);

		List<ChaincodeInfo> chaincodeList = channel.queryInstantiatedChaincodes(peer);

		boolean found = false;
		for (ChaincodeInfo chaincodeInfo : chaincodeList) {
			logger.debug("Peer: {} 已实例化 chaincode：{}:{}", peer.getName(), chaincodeInfo.getName(), chaincodeInfo.getVersion());

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
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:48:29
	 */
	public boolean checkInstantiatedChaincode(Channel channel, ChaincodeID chaincodeId) throws Exception {
		
		boolean found = false;
		for (Peer peer : channel.getPeers()) {
			if (checkInstantiatedChaincode(channel, peer, chaincodeId)) {
				found = true;
			}
		}
		
		return found;
	}
	
	/**
	 * 检查Chaincode是否在通道上成功安装和实例化
	 * @author hoojo
	 * @createDate 2018年6月25日 上午10:47:40
	 */
	public boolean checkChaincode(Channel channel, ChaincodeID chaincodeId, Organization org) throws Exception {
		logger.info("安装和实例化检查 —— channel：{}，Chaincode： {}", channel.getName(), chaincodeId.toString());

		// 设置对等节点用户上下文
		client.setUserContext(org.getPeerAdmin());

		boolean found = false;
		for (Peer peer : channel.getPeers()) {
			if (checkInstallChaincode(peer, chaincodeId) && checkInstantiatedChaincode(channel, peer, chaincodeId)) {
				found = true;
			}
		}

		return found;
	}
}
