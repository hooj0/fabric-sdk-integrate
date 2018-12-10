package com.cnblogs.hoojo.fabric.sdk.entity;

import java.io.File;
import java.io.InputStream;

import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;

/**
 * 安装chaincode模型实体对象
 * @author hoojo
 * @createDate 2018年6月25日 下午3:51:43
 * @file InstallEntity.java
 * @package com.cnblogs.hoojo.fabric.sdk.entity
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class InstallEntity extends ChaincodeEntity {
	
	/** 在升级chaincode时，安装不同的版本 */
	private String chaincodeVersion;
	/** chaincode source code file， chaincodeSourceFile 和  chaincodeSourceStream 二选一*/
	private File chaincodeSourceFile;
	/** chaincode source code file stream， chaincodeSourceFile 和  chaincodeSourceStream 二选一 */
	private InputStream chaincodeSourceStream;
	private File chaincodeMetaINF;
	
	public InstallEntity(ChaincodeID chaincodeId, Type language) {
		super(chaincodeId, language);
	}
	
	public InstallEntity(ChaincodeID chaincodeId, Type language, String chaincodeVersion) {
		super(chaincodeId, language);
		this.chaincodeVersion = chaincodeVersion;
	}

	public File getChaincodeSourceFile() {
		return chaincodeSourceFile;
	}

	public void setChaincodeSourceFile(File chaincodeSourceFile) {
		this.chaincodeSourceFile = chaincodeSourceFile;
	}

	public InputStream getChaincodeSourceStream() {
		return chaincodeSourceStream;
	}

	public void setChaincodeSourceStream(InputStream chaincodeSourceStream) {
		this.chaincodeSourceStream = chaincodeSourceStream;
	}

	public String getChaincodeVersion() {
		return chaincodeVersion;
	}

	public void setChaincodeVersion(String chaincodeVersion) {
		this.chaincodeVersion = chaincodeVersion;
	}

	public File getChaincodeMetaINF() {
		return chaincodeMetaINF;
	}

	public void setChaincodeMetaINF(File chaincodeMetaINF) {
		this.chaincodeMetaINF = chaincodeMetaINF;
	}
}
