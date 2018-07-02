package com.cnblogs.hoojo.fabric.sdk.integration;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.junit.Test;

/**
 * <b>function:</b>
 * @author hoojo
 * @createDate 2018年6月29日 下午3:22:24
 * @file NodeJSChaincodeIntegrationExamples.java
 * @package com.cnblogs.hoojo.fabric.sdk.integration
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class NodeJSChaincodeIntegrationExamples extends GoChaincodeIntegrationExamples {

	{
		CHAIN_CODE_LANG = Type.NODE;
		CHAIN_CODE_NAME = "example_cc_node";
		CHAIN_CODE_PATH = null;

		id = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION).build();
		id_11 = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION_11).build();
	}
	

	@Test
	public void testInstallChaincode() {
		super.testInstallChaincode();
	}
	
	@Test
	public void testInstantiateChaincode() {
		super.testInstantiateChaincode();
	}
	
	@Test
	public void testQueryChaincode() {
		super.testQueryChaincode();
	}
	
	@Test
	public void testInvokeChaincode() {
		super.testInvokeChaincode();
	}
}
