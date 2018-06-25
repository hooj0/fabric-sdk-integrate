package com.cnblogs.hoojo.fabric.sdk.entity;

import org.hyperledger.fabric.sdk.Channel.TransactionOptions;

import java.util.Collection;

import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.User;

import com.cnblogs.hoojo.fabric.sdk.common.AbstractFabricObject;

/**
 * chaincode 交易的一些选项参数
 * @author hoojo
 * @createDate 2018年6月25日 下午4:41:50
 * @file ChaincodeTransaction.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class ChaincodeTransaction extends AbstractFabricObject {

	private Collection<Orderer> orderers;
	private TransactionOptions options;
	private User user;
	
	public TransactionOptions getOptions() {
		return options;
	}
	public void setOptions(TransactionOptions options) {
		this.options = options;
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public Collection<Orderer> getOrderers() {
		return orderers;
	}
	public void setOrderers(Collection<Orderer> orderers) {
		this.orderers = orderers;
	}
}
