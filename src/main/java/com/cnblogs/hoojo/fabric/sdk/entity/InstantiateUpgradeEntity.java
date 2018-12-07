package com.cnblogs.hoojo.fabric.sdk.entity;

import org.hyperledger.fabric.sdk.ChaincodeCollectionConfiguration;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.User;

/**
 * 实例化Chaincode模型对象,升级chaincode实体对象封装
 * @author hoojo
 * @createDate 2018年6月25日 下午4:09:47
 * @file InstantiateUpgradeEntity.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class InstantiateUpgradeEntity extends TransactionEntity {

	private ChaincodeCollectionConfiguration collectionConfiguration;
	private ChaincodeEndorsementPolicy endorsementPolicy;
	private User user;
	
	public InstantiateUpgradeEntity(ChaincodeID chaincodeId, Type language) {
		super(chaincodeId, language);
	}

	public InstantiateUpgradeEntity(ChaincodeID chaincodeId, Type language, String func, String[] args) {
		super(chaincodeId, language, func, args);
	}
	
	public ChaincodeEndorsementPolicy getEndorsementPolicy() {
		return endorsementPolicy;
	}

	public void setEndorsementPolicy(ChaincodeEndorsementPolicy endorsementPolicy) {
		this.endorsementPolicy = endorsementPolicy;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public ChaincodeCollectionConfiguration getCollectionConfiguration() {
		return collectionConfiguration;
	}

	public void setCollectionConfiguration(ChaincodeCollectionConfiguration collectionConfiguration) {
		this.collectionConfiguration = collectionConfiguration;
	}
}
