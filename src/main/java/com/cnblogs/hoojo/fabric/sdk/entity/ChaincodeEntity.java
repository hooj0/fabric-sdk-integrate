package com.cnblogs.hoojo.fabric.sdk.entity;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;

import com.cnblogs.hoojo.fabric.sdk.common.AbstractFabricObject;

/**
 * Chaincode 实体对象
 * @author hoojo
 * @createDate 2018年6月25日 下午4:12:40
 * @file ChaincodeEntity.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class ChaincodeEntity extends AbstractFabricObject {

	/** chaincode ID */
	protected ChaincodeID chaincodeId;
	/** chaincode language */
	protected Type language;
	
	public ChaincodeEntity(ChaincodeID chaincodeId, Type language) {
		super();
		this.chaincodeId = chaincodeId;
		this.language = language;
	}

	public ChaincodeID getChaincodeId() {
		return chaincodeId;
	}

	public void setChaincodeId(ChaincodeID chaincodeId) {
		this.chaincodeId = chaincodeId;
	}

	public Type getLanguage() {
		return language;
	}

	public void setLanguage(Type language) {
		this.language = language;
	}
}
