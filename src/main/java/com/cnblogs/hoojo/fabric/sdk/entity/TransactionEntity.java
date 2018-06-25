package com.cnblogs.hoojo.fabric.sdk.entity;

import java.util.Map;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;

/**
 * chaincode 交易类型对象封装
 * @author hoojo
 * @createDate 2018年6月25日 下午4:19:17
 * @file TransactionEntity.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class TransactionEntity extends ChaincodeEntity {

	/** 请求中的瞬时账本数据 */
	private Map<String, byte[]> transientMap;
	/** 交易调用的方法 */
	private String func;
	/** 交易参数 */
	private String[] args;
	/** 在特定的Peer节点上执行chaincode */
	private boolean specificPeers;
	
	public TransactionEntity(ChaincodeID chaincodeId, Type language) {
		super(chaincodeId, language);
	}
	
	public TransactionEntity(ChaincodeID chaincodeId, Type language, String func, String[] args) {
		super(chaincodeId, language);
		this.func = func;
		this.args = args;
	}

	public String getFunc() {
		return func;
	}

	public void setFunc(String func) {
		this.func = func;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	public Map<String, byte[]> getTransientMap() {
		return transientMap;
	}

	public void setTransientMap(Map<String, byte[]> transientMap) {
		this.transientMap = transientMap;
	}
	public boolean isSpecificPeers() {
		return specificPeers;
	}

	public void setSpecificPeers(boolean specificPeers) {
		this.specificPeers = specificPeers;
	}
}
