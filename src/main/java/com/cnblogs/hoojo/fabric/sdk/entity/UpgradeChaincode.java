package com.cnblogs.hoojo.fabric.sdk.entity;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;

/**
 * 升级chaincode实体对象封装
 * @author hoojo
 * @createDate 2018年6月25日 下午5:15:52
 * @file UpgradeChaincode.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class UpgradeChaincode extends InstantiateChaincode {

	public UpgradeChaincode(ChaincodeID chaincodeId, Type language, String func, String[] args) {
		super(chaincodeId, language, func, args);
	}
}
