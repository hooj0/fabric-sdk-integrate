package com.cnblogs.hoojo.fabric.sdk.common;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.cnblogs.hoojo.fabric.sdk.log.ApplicationLogging;

/**
 * <b>function:</b> model 实体基类
 * @author hoojo
 * @createDate 2018年6月12日 下午3:09:27
 * @file AbstractModel.java
 * @package com.cnblogs.hoojo.fabric.sdk.model
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public abstract class AbstractModel extends ApplicationLogging {

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
