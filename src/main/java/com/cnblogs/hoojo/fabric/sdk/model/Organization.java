package com.cnblogs.hoojo.fabric.sdk.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import com.cnblogs.hoojo.fabric.sdk.common.AbstractFabricObject;

/**
 * Organization Resources 组织资源配置
 * @author hoojo
 * @createDate 2018年6月12日 下午3:26:54
 * @file Organization.java
 * @package com.cnblogs.hoojo.fabric.sdk.config
 * @project fabric-sdk-examples
 * @blog http://hoojo.cnblogs.com
 * @email hoojo_@126.com
 * @version 1.0
 */
public class Organization extends AbstractFabricObject {
	
	/** org name */
    private String name;
    /** org msp id */
    private String mspid;
    /** ca client */
    private HFCAClient caClient;

    private Map<String, User> userMap = new HashMap<>();
    /** peer 配置位置 */
    private Map<String, String> peerLocations = new HashMap<>();
    /** orderer 配置位置 */
    private Map<String, String> ordererLocations = new HashMap<>();
    /** event 总线配置位置 */
    private Map<String, String> eventHubLocations = new HashMap<>();
    
    /** ca 管理员 */
    private OrganizationUser admin;
    /** peer 节点管理员 */
    private OrganizationUser peerAdmin;

    /** ca 节点名称 */
    private String caName;
    /** ca 配置位置 */
    private String caLocation;
    private Properties caProperties = null;

    /** 域名 */
    private String domainName;

    public Organization(String name, String mspid) {
        this.name = name;
        this.mspid = mspid;
    }
    
    /** ca 节点名称 */
    public String getCAName() {
        return caName;
    }

    /** ca 管理员 */
    public OrganizationUser getAdmin() {
        return admin;
    }

    /** ca 管理员 */
    public void setAdmin(OrganizationUser admin) {
        this.admin = admin;
    }

    /** org msp id */
    public String getMSPID() {
        return mspid;
    }

    /** ca 配置位置 */
    public String getCALocation() {
        return this.caLocation;
    }

    /** ca 配置位置 */
    public void setCALocation(String caLocation) {
        this.caLocation = caLocation;
    }

    public void addPeerLocation(String name, String location) {
        peerLocations.put(name, location);
    }

    public void addOrdererLocation(String name, String location) {
        ordererLocations.put(name, location);
    }

    public void addEventHubLocation(String name, String location) {
        eventHubLocations.put(name, location);
    }

    public String getPeerLocation(String name) {
        return peerLocations.get(name);
    }

    public String getOrdererLocation(String name) {
        return ordererLocations.get(name);
    }

    public String getEventHubLocation(String name) {
        return eventHubLocations.get(name);
    }

    public Set<String> getPeerNames() {
        return Collections.unmodifiableSet(peerLocations.keySet());
    }


    public Set<String> getOrdererNames() {
        return Collections.unmodifiableSet(ordererLocations.keySet());
    }

    public Set<String> getEventHubNames() {
        return Collections.unmodifiableSet(eventHubLocations.keySet());
    }

    public HFCAClient getCAClient() {
        return caClient;
    }

    public void setCAClient(HFCAClient caClient) {
        this.caClient = caClient;
    }

    public String getName() {
        return name;
    }

    public void addUser(OrganizationUser user) {
        userMap.put(user.getName(), user);
    }

    public User getUser(String name) {
        return userMap.get(name);
    }

    public Collection<String> getOrdererLocations() {
        return Collections.unmodifiableCollection(ordererLocations.values());
    }

    public Collection<String> getEventHubLocations() {
        return Collections.unmodifiableCollection(eventHubLocations.values());
    }

    public void setCAProperties(Properties caProperties) {
        this.caProperties = caProperties;
    }

    public Properties getCAProperties() {
        return caProperties;
    }

    public OrganizationUser getPeerAdmin() {
        return peerAdmin;
    }

    public void setPeerAdmin(OrganizationUser peerAdmin) {
        this.peerAdmin = peerAdmin;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setCAName(String caName) {
        this.caName = caName;
    }
}
