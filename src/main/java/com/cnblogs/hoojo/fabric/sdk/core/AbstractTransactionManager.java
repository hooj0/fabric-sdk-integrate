package com.cnblogs.hoojo.fabric.sdk.core;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.ProposalResponse;

import com.cnblogs.hoojo.fabric.sdk.config.DefaultConfiguration;
import com.cnblogs.hoojo.fabric.sdk.entity.ChaincodeTransaction;
import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;

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
	
	public AbstractTransactionManager(DefaultConfiguration config) {
		super();
		this.config = config;
	}

	/**
	 * 执行交易初始化动作，返回交易事件
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public TransactionEvent sendTransaction(Channel channel, Collection<ProposalResponse> responses, ChaincodeTransaction transaction) throws Exception {
		return sendTransaction(responses, transaction, channel).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
	}
	
	/**
	 * 执行交易初始化方法，返回多线程模型
	 * @author hoojo
	 * @createDate 2018年6月15日 上午11:54:46
	 */
	public CompletableFuture<TransactionEvent> sendTransaction(Collection<ProposalResponse> responses, ChaincodeTransaction transaction, Channel channel) throws Exception {

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
}
