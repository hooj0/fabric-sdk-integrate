package com.cnblogs.hoojo.fabric.sdk.event;

import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;

/**
 * <b>function:</b>
 * 
 * @author hoojo
 * @createDate 2018年6月13日 下午4:53:34
 * @file BaseChaincodeEvent.java
 * @package com.cnblogs.hoojo.fabric.sdk.event
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class BaseChaincodeEvent {
	
	private String handle;
	private BlockEvent blockEvent;
	private ChaincodeEvent chaincodeEvent;

	public BaseChaincodeEvent(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
		this.handle = handle;
		this.blockEvent = blockEvent;
		this.chaincodeEvent = chaincodeEvent;
	}

	public String getHandle() {
		return handle;
	}

	public BlockEvent getBlockEvent() {
		return blockEvent;
	}

	public ChaincodeEvent getChaincodeEvent() {
		return chaincodeEvent;
	}
}
