/*******************************************************************************
 * Copyright (c) <2013>, California Institute of Technology ("Caltech"). U.S. Government sponsorship
 * acknowledged.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. - Neither the name of Caltech nor its operating
 * division, the Jet Propulsion Laboratory, nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package gov.nasa.jpl.view_repo.util;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.jscript.ScriptVersion;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.springframework.extensions.webscripts.Status;

import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.HasId;
import gov.nasa.jpl.mbee.util.HasName;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.view_repo.webscripts.AbstractJavaWebScript;

/**
 * Extension of ScriptNode to support EMS needs
 *
 * @author cinyoung
 */
public class EmsScriptNode extends ScriptNode
    implements Comparator<EmsScriptNode>, Comparable<EmsScriptNode>, HasName<String>, HasId<String> {
    private static final long serialVersionUID = 9132455162871185541L;

    public static final String ADMIN_USER_NAME = "admin";

    static Logger logger = Logger.getLogger(ScriptNode.class);
    public static boolean expressionStuffDefault = false; // The value here is ignored.

    public static boolean optimisticAndFoolish = false;

    public static boolean tryToFlushCache = false;

    public static boolean versionCacheDebugPrint = false;

    public boolean renamed = false;

    /**
     * A set of content model property names that serve as workspace metadata and whose changes are
     * not recorded in a workspace.
     */
    public static TreeSet<String> workspaceMetaProperties = new TreeSet<String>() {
        private static final long serialVersionUID = -327817873667229953L;

        {
            add("ems:workspace");
            add("ems:source");
            add("ems:parent");
            add("ems:children");
            add("ems:lastTimeSyncParent");
            add("ems:mergeSource");
        }
    };


    public static class EmsVersion implements Comparator<EmsVersion>, Comparable<EmsVersion> {
        public NodeRef nodeRef = null;
        public NodeRef frozenNodeRef = null;
        public EmsScriptNode emsNode = null;
        public Version version = null;
        public String label = null;
        public Date date = null;

        public EmsVersion(NodeRef nodeRef, NodeRef frozenNodeRef, Version version) {
            super();
            this.nodeRef = nodeRef;
            this.frozenNodeRef = frozenNodeRef;
            this.version = version;
        }

        public NodeRef getNodeRef() {
            return nodeRef;
        }

        public NodeRef getFrozenNodeRef() {
            if (frozenNodeRef == null) {
                if (getVersion() != null) {
                    frozenNodeRef = getVersion().getFrozenStateNodeRef();
                }
            }
            return frozenNodeRef;
        }

        public EmsScriptNode getEmsNode() {
            if (emsNode == null && getNodeRef() != null) {
                emsNode = new EmsScriptNode(getNodeRef(), NodeUtil.getServices());
            }
            return emsNode;
        }

        public Version getVersion() {
            return version;
        }

        public void setVersion(Version version) {
            this.version = version;
        }

        public String getLabel() {
            if (label == null) {
                if (getVersion() != null) {
                    label = getVersion().getVersionLabel();
                }
            }
            return label;
        }

        public Date getDate() {
            if (date == null) {
                if (getVersion() != null) {
                    date = getVersion().getFrozenModifiedDate();
                }
            }
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        @Override public int compareTo(EmsVersion v2) {
            EmsVersion v1 = this;
            if (v1 == v2)
                return 0;
            if (v2 == null)
                return 1;
            Date d1 = v1.getDate();
            Date d2 = v2.getDate();
            if (d1 != null && d2 != null) {
                return d1.compareTo(d2);
            }
            if (v1.getLabel() != null && v2.getLabel() != null) {
                return v1.getLabel().compareTo(v2.getLabel());
            }
            if (v1.getEmsNode() == v1.getEmsNode())
                return 0;
            if (v1.getEmsNode() == null)
                return -1;
            if (v2.getEmsNode() == null)
                return 1;
            return v1.getEmsNode().compareTo(v2.getEmsNode());
        }

        @Override public int compare(EmsVersion v1, EmsVersion v2) {
            if (v1 == v2)
                return 0;
            if (v1 == null)
                return -1;
            if (v2 == null)
                return 1;
            return v1.compareTo(v2);
        }
    }

    // provide logging capability of what is done
    private StringBuffer response = null;

    // provide status as necessary
    private Status status = null;

    /**
     * whether to use the foundational Alfresco Java API or ScriptNode class that uses the
     * JavaScript API
     */
    public boolean useFoundationalApi = true; // TODO this will be removed

    protected EmsScriptNode workspace = null;

    /**
     * Replicates the behavior of ScriptNode versions, which is private.
     */
    protected Object[] myVersions = null;

    public AbstractJavaWebScript webscript = null;

    // private boolean forceCacheUpdate = false;

    public static boolean fixOwnedChildren = false;

    // TODO add nodeService and other member variables when no longer
    // subclassing ScriptNode
    // extend Serializable after removing ScriptNode extension

    public EmsScriptNode(NodeRef nodeRef, ServiceRegistry services, StringBuffer response, Status status) {
        this(nodeRef, services);
        setStatus(status);
    }

    public EmsScriptNode(NodeRef nodeRef, ServiceRegistry services, StringBuffer response) {
        this(nodeRef, services);
        setResponse(response);
    }

    /**
     * Gets the version history
     *
     * This is needed b/c the ScriptNode getVersionHistory() generates a NPE
     *
     * @return version history
     */
    public Object[] getEmsVersionHistory() {

        if (this.myVersions == null && getIsVersioned()) {
            VersionHistory history = this.services.getVersionService().getVersionHistory(this.nodeRef);
            if (history != null) {
                Collection<Version> allVersions = history.getAllVersions();
                Object[] versions = new Object[allVersions.size()];
                int i = 0;
                for (Version version : allVersions) {
                    versions[i++] = new ScriptVersion(version, this.services, this.scope);
                }
                this.myVersions = versions;
            }
        }
        return this.myVersions;

    }

    /**
     * Gets the version history
     *
     * This is needed b/c the ScriptNode getVersionHistory() generates a NPE
     *
     * @return version history
     */
    public NavigableMap<Long, Version> getVersionPropertyHistory() {

        if (getIsVersioned()) {
            VersionHistory history = this.services.getVersionService().getVersionHistory(this.nodeRef);
            if (history != null) {
                // This is ordered by the label or version number
                Collection<Version> allVersions = history.getAllVersions();
                NavigableMap<Long, Version> versions = new TreeMap<>();
                for (Version version : allVersions) {
                    versions.put(version.getFrozenModifiedDate().getTime(), version);
                }

                return versions;
            }
        }
        return null;
    }



    /**
     * Create a version of this document. Note: this will add the cm:versionable aspect.
     *
     * @param history      Version history note
     * @param majorVersion True to save as a major version increment, false for minor version.
     * @return ScriptVersion object representing the newly added version node
     */
    @Override public ScriptVersion createVersion(String history, boolean majorVersion) {
        this.myVersions = null;
        transactionCheck();
        return super.createVersion(history, majorVersion);
    }

    /**
     * Check-in a working copy document. The current state of the working copy is copied to the
     * original node, this will include any content updated in the working node. Note that this
     * method can only be called on a working copy Node.
     *
     * @param history      Version history note
     * @param majorVersion True to save as a major version increment, false for minor version.
     * @return the original Node that was checked out.
     */
    @Override public ScriptNode checkin(String history, boolean majorVersion) {
        this.myVersions = null;
        transactionCheck();
        return super.checkin(history, majorVersion);
    }

    /**
     * @see org.alfresco.repo.jscript.ScriptNode#childByNamePath(java.lang.String)
     */
    @Override public EmsScriptNode childByNamePath(String path) {
        String runAsUser = AuthenticationUtil.getRunAsUser();
        boolean changeUser = !ADMIN_USER_NAME.equals(runAsUser);
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(ADMIN_USER_NAME);
        }

        ScriptNode child = super.childByNamePath(path);

        if (changeUser) {
            AuthenticationUtil.setRunAsUser(runAsUser);
        }

        if (child == null || !child.exists()) {
            return null;
        }
        EmsScriptNode childNode = new EmsScriptNode(child.getNodeRef(), services, response);

        childNode.getOrSetCachedVersion();

        return childNode;
    }

    @Override public EmsScriptNode createFile(String name) {
        makeSureNodeRefIsNotFrozen();
        transactionCheck();
        EmsScriptNode fileNode = new EmsScriptNode(super.createFile(name).getNodeRef(), services, response, status);

        fileNode.getOrSetCachedVersion();

        return fileNode;
    }

    @Override public EmsScriptNode createFolder(String name) {
        return createFolder(name, null);
    }

    @Override public EmsScriptNode createFolder(String name, String type) {
        return createFolder(name, type, null);
    }

    public EmsScriptNode createFolder(String name, String type, NodeRef sourceFolder) {
        if (logger.isInfoEnabled()) {
            logger.info("creating " + name + " in " + sourceFolder);
        }

        makeSureNodeRefIsNotFrozen();
        NodeRef folderRef = super.createFolder(name, type).getNodeRef();
        transactionCheck();
        EmsScriptNode folder = new EmsScriptNode(folderRef, services, response, status);

        return folder;
    }

    protected void updateBogusProperty(String type) {
        // Make sure the aspect change makes it into the version history by
        // updating a bogus property.
        String bogusPropName = null;
        if (Acm.ASPECTS_WITH_BOGUS_PROPERTY.containsKey(type)) {
            bogusPropName = Acm.ASPECTS_WITH_BOGUS_PROPERTY.get(type);
        }
        if (bogusPropName == null)
            return;
        Random rand = new Random();
        int randNum = rand.nextInt(10000000);
        setProperty(Acm.ASPECTS_WITH_BOGUS_PROPERTY.get(type), randNum);
    }

    /**
     * Check whether or not a node has a property, update or create as necessary
     *
     * NOTE: this only works for non-collection properties - for collections handwrite (or see how
     * it's done in ModelPost.java)
     *
     * @param acmType Short name for the Alfresco Content Model type
     * @param value   Value to set property to
     * @return true if property updated, false otherwise (e.g., value did not change)
     */
    public <T extends Serializable> boolean createOrUpdateProperty(String acmType, T value) {

        // It is important we ignore the workspace when getting the property, so
        // we make sure
        // to update this property when needed. Otherwise, property may have a
        // noderef in
        // a parent workspace, and this wont detect it; however, all the
        // getProperty() will look
        // for the correct workspace node, so perhaps this is overkill:
        T oldValue = (T) getNodeRefProperty(acmType, true, null, false, true, null);
        if (oldValue != null && value != null) {
            if (!value.equals(oldValue)) {
                setProperty(acmType, value);
                log(getName() + ": " + acmType + " property updated to value = " + value);
                return true;
            }
        }
        // Note: Per CMED-461, we are allowing properties to be set to null
        else {
            log(getName() + ": " + acmType + " property created with value = " + value);
            boolean changed = setProperty(acmType, value);
            // If setting the property to null, the modified time is not changed
            // by alfresco if
            // it was previously null, which is the initial state of the
            // property, but we want
            // the modification time to be altered in this case too:
            if (oldValue == null && value == null) {
                setProperty(Acm.ACM_LAST_MODIFIED, new Date(), false, 0);
            }
            if (!changed) {
                logger.warn(
                    "Failed to set property for new value in createOrUpdateProperty(" + acmType + ", " + value + ")");
            }
            return changed;
        }

        return false;
    }

    public static String getMimeType(String type) {
        Field[] fields = ClassUtils.getAllFields(MimetypeMap.class);
        for (Field f : fields) {
            if (f.getName().startsWith("MIMETYPE")) {
                if (ClassUtils.isStatic(f) && f.getName().substring(8).toLowerCase().contains(type.toLowerCase())) {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalArgumentException e) {
                    } catch (IllegalAccessException e) {
                    }
                }
            }
        }
        return null;
    }



    public static long getChecksum(byte[] data) {
        long cs = 0;
        Checksum checksum = new CRC32();
        checksum.update(data, 0, data.length);
        cs = checksum.getValue();
        return cs;
    }

    /**
     * Override createNode to return an EmsScriptNode
     *
     * @param name cm:name of node (which may also be the sysml:id)
     * @param type Alfresco Content Model type of node to create
     * @return created child EmsScriptNode
     */
    @Override public EmsScriptNode createNode(String name, String type) {


        EmsScriptNode result = null;

        if (!useFoundationalApi) {
            makeSureNodeRefIsNotFrozen();
            ScriptNode scriptNode = super.createNode(name, type);
            transactionCheck();
            result = new EmsScriptNode(scriptNode.getNodeRef(), services, response);
        } else {
            Map<QName, Serializable> props = new HashMap<>(1, 1.0f);
            props.put(ContentModel.PROP_NAME, name);

            QName typeQName = createQName(type);
            if (typeQName != null) {
                try {
                    makeSureNodeRefIsNotFrozen();
                    transactionCheck();
                    ChildAssociationRef assoc = services.getNodeService()
                        .createNode(nodeRef, ContentModel.ASSOC_CONTAINS,
                            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, QName.createValidLocalName(name)),
                            createQName(type), props);
                    result = new EmsScriptNode(assoc.getChildRef(), services, response);
                } catch (Exception e) {
                    logger.error(
                        "Got exception in " + "createNode(name=" + name + ", type=" + type + ") for EmsScriptNode("
                            + this + ") calling createNode(nodeRef=" + nodeRef + ", . . .)");
                    e.printStackTrace();
                }

            } else {
                log("Could not find type " + type);
            }
        }

        return result;
    }

    public void makeSureNodeRefIsNotFrozen() {
        if (!NodeUtil.doHeisenCheck && !NodeUtil.doVersionCaching) {
            return;
        }
        NodeRef liveNodeRef = getLiveNodeRefFromVersion();
        if (liveNodeRef != null && !liveNodeRef.equals(nodeRef)) {
            EmsScriptNode liveNode = new EmsScriptNode(liveNodeRef, getServices());
            if (isAVersion()) {
                logger.error("Trying to create a node under a frozen node ref (" + nodeRef + ", v " + getVersionLabel()
                    + ")! Replacing nodeRef with (v " + liveNode.getVersionLabel() + ") live node ref (" + liveNodeRef
                    + "), which may not point to the right version! " + this);
                Debug.error(true, "Stacktrace for frozen node replacement:");
                nodeRef = liveNodeRef;
            } else {
                logger.error(
                    "Live node " + liveNode.getVersionLabel() + " is different from current " + getVersionLabel()
                        + " node ref!" + this);
            }
        }
    }

    @Override public String getName() {
        super.getName();
        return (String) getProperty(Acm.CM_NAME);
    }

    public String getSysmlId() {
        String id = (String) getProperty(Acm.ACM_ID);
        if (id == null) {
            id = getName();
        }
        return id;
    }

    @Override public EmsScriptNode getParent() {
        ScriptNode myParent = super.getParent();
        if (myParent == null)
            return null;
        return new EmsScriptNode(myParent.getNodeRef(), services, response);
    }

    /**
     * This version of getParent() handles versioned nodes correctly by calling getOwningParent()
     * first.
     *
     * @param dateTime
     * @param ws
     * @param skipNodeRefCheck
     * @param checkVersionedNode
     * @return
     */
    public EmsScriptNode getParent(Date dateTime, EmsScriptNode ws, boolean skipNodeRefCheck,
        boolean checkVersionedNode) {
        return getParent();
    }



    public boolean isAVersion() {

        VersionService vs = services.getVersionService();
        return vs.isAVersion(nodeRef) || nodeRef.getStoreRef().getIdentifier().equals(Version2Model.STORE_ID);

    }

    public String getVersionLabel() {
        Version v = getCurrentVersion();
        if (v != null) {
            return v.getVersionLabel();
        }
        return null;
    }

    public NodeRef getLiveNodeRefFromVersion() {
        if (!isAVersion()) {
            return nodeRef;
        }
        Version v = getCurrentVersion();
        if (v != null) {
            NodeRef liveRef = v.getVersionedNodeRef();
            if (liveRef != null) {
                return liveRef;
            }
        }
        NodeRef ref = NodeUtil.getCurrentNodeRefFromCache(nodeRef);
        if (ref != null) {
            return ref;
        }
        return nodeRef;
    }

    protected boolean updateFrozenCache(Version currentVersion) {
        if (currentVersion != null) {
            NodeRef frozenRef = currentVersion.getFrozenStateNodeRef();
            if (!this.isAVersion()) {
                NodeUtil.frozenNodeCache.put(frozenRef, nodeRef);
                return true;
            }
            NodeRef versionedRef = currentVersion.getVersionedNodeRef();
            if (versionedRef != null) {
                NodeUtil.frozenNodeCache.put(frozenRef, versionedRef);
                return true;
            }
        }
        return false;
    }

    public Version getCurrentVersion() {
        VersionService versionService = services.getVersionService();

        Version currentVersion = null;
        if (versionService != null) {
            try {
                currentVersion = versionService.getCurrentVersion(nodeRef);
                updateFrozenCache(currentVersion);
            } catch (Throwable t1) {
                try {
                    currentVersion = versionService.getCurrentVersion(nodeRef);
                    updateFrozenCache(currentVersion);
                } catch (Throwable t2) {
                    logger.error("1. Got exception in getCurrentVersion(): " + t1.getLocalizedMessage());
                    t1.printStackTrace();
                    logger.error(
                        "2. Tried again and got another exception in getCurrentVersion(): " + t2.getLocalizedMessage());
                    t2.printStackTrace();
                }
            }
        }
        return currentVersion;
    }

    public void transactionCheck() {
        NodeUtil.transactionCheck(logger, this);
    }

    public boolean getOrSetCachedVersion() {
        if (versionCacheDebugPrint)
            System.out.println("0: getOrSetCachedVersion(): " + this + " :: " + this.getId());
        if (!NodeUtil.doVersionCaching || isAVersion()) {
            if (versionCacheDebugPrint)
                System.out.println("1: N/A " + this.getName());
            return false;
        }
        String id = getId();
        EmsVersion cachedVersion = NodeUtil.versionCache.get(id);
        Version thisVersion = getCurrentVersion();
        EmsVersion thisEmsVersion = null;
        if (thisVersion != null) {
            thisEmsVersion = new EmsVersion(nodeRef, null, thisVersion);
        }

        if (cachedVersion == null) {
            if (thisVersion == null) {
                if (versionCacheDebugPrint)
                    System.out.println("2: no version");
                return false;
            }
            if (optimisticAndFoolish) {
                NodeUtil.versionCache.put(id, thisEmsVersion);
                if (versionCacheDebugPrint)
                    System.out.println("9: optimisticAndFoolish");
            } else {
                cachedVersion = new EmsVersion(nodeRef, null, getHeadVersion());
                NodeUtil.versionCache.put(id, cachedVersion);
                String msg =
                    "3: initializing version cache with node, " + this + " version: " + cachedVersion.getLabel();
                if (logger.isInfoEnabled())
                    logger.info(msg);
                if (versionCacheDebugPrint)
                    System.out.println(msg);

            }
        }
        if (cachedVersion == null) {
            return false;
        }
        if (thisEmsVersion == null) {
            String msg = "6: Warning! Alfresco Heisenbug failing to return current version of node " + this.getNodeRef()
                + ".  Replacing node with unmodifiable frozen node, " + cachedVersion.getLabel() + ".";
            logger.error(msg);
            if (response != null) {
                response.append(msg + "\n");
            }
            nodeRef = cachedVersion.getFrozenNodeRef();
            if (tryToFlushCache)
                NodeUtil.clearAlfrescoNodeCache();
            return true;
        }
        int comp = thisEmsVersion.compareTo(cachedVersion);
        if (comp == 0) {
            if (versionCacheDebugPrint)
                System.out.println("3: same version " + thisEmsVersion.getLabel());
            return false;
        }
        if (comp < 0) {
            logger.error("inTransaction = " + NodeUtil.isInsideTransactionNow());
            logger.error("haveBeenInTransaction = " + NodeUtil.isInsideTransactionNow());
            logger.error("haveBeenOutsideTransaction = " + NodeUtil.isInsideTransactionNow());
            // Cache is correct -- fix esn's nodeRef
            String msg = "4: Warning! Alfresco Heisenbug returning wrong current version of node, " + this + " ("
                + thisEmsVersion.getLabel() + ").  Replacing node with unmodifiable frozen node, " + getId() + " ("
                + cachedVersion.getLabel() + ").";
            logger.error(msg);
            Debug.error(true, msg);
            // NodeUtil.sendNotificationEvent( "Heisenbug Occurrence!", "" );
            if (response != null) {
                response.append(msg + "\n");
            }
            nodeRef = cachedVersion.getFrozenNodeRef();
            if (tryToFlushCache)
                NodeUtil.clearAlfrescoNodeCache();
        } else { // comp > 0
            // Cache is incorrect -- update cache
            NodeUtil.versionCache.put(id, thisEmsVersion);
            String msg = "5: Updating version cache with new version of node, " + this + " version: " + thisEmsVersion
                .getLabel();
            if (logger.isInfoEnabled())
                logger.info(msg);
            if (versionCacheDebugPrint)
                System.out.println(msg);
        }
        return true;
    }


    /**
     * Getting a noderef property needs to be contextualized by the workspace and time This works
     * for any property type noderef or otherwise, so use this if you want to be safe.
     *
     * @param acmType
     * @param ignoreWorkspace
     * @param dateTime
     * @param findDeleted
     * @param skipNodeRefCheck
     * @param ws
     * @return
     */
    public Object getNodeRefProperty(String acmType, boolean ignoreWorkspace, Date dateTime, boolean findDeleted,
        boolean skipNodeRefCheck, EmsScriptNode ws) {
        // Make sure we have the right node ref before getting a property from
        // it.

        Object result = getPropertyImpl(acmType, true); // TODO -- This should
        // be passing in
        // cacheOkay from the
        // caller instead of
        // true!



        return result;
    }

    /**
     * Get the property of the specified type for non-noderef properties. Throws unsupported
     * operation exception otherwise (go and fix the code if that happens).
     *
     * @param acmType   Short name of property to get
     * @return
     */
    public Object getProperty(String acmType) {
        return getProperty(acmType, true);
    }

    /**
     * Get the property of the specified type for non-noderef properties. Throws unsupported
     * operation exception otherwise (go and fix the code if that happens).
     *
     * @param acmType   Short name of property to get
     * @param cacheOkay
     * @return
     */
    public Object getProperty(String acmType, boolean cacheOkay) {
        Object result = getPropertyImpl(acmType, cacheOkay);

        // Throw an exception of the property value is a NodeRef or
        // collection of NodeRefs
        // TODO -- REVIEW -- Can the if-statements be reordered to make this
        // more efficient?
        if (!workspaceMetaProperties.contains(acmType)) {
            if (result instanceof NodeRef) {
                throw new UnsupportedOperationException();
            } else if (result instanceof Collection) {
                Collection<?> resultColl = (Collection<?>) result;
                if (!Utils.isNullOrEmpty(resultColl)) {
                    Object firstResult = resultColl.iterator().next();
                    if (firstResult instanceof NodeRef) {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        }

        return result;
    }

    private Object getPropertyImpl(String acmType, boolean cacheOkay) {
        return NodeUtil.getNodeProperty(this, acmType, getServices(), useFoundationalApi, cacheOkay);
    }

    /**
     * Get the properties of this node
     *
     * @return
     */
    @Override public Map<String, Object> getProperties() {

        if (useFoundationalApi) {
            return Utils.toMap(services.getNodeService().getProperties(nodeRef), String.class, Object.class);
        } else {
            return super.getProperties();
        }
    }

    public StringBuffer getResponse() {
        return response;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * Append onto the response for logging purposes
     *
     * @param msg Message to be appened to response TODO: fix logger for EmsScriptNode
     */
    public void log(String msg) {
    }

    /**
     * Genericized function to set property for non-collection types
     *
     * @param acmType Property short name for alfresco content model type
     * @param value   Value to set property to
     */
    public <T extends Serializable> boolean setProperty(String acmType, T value) {
        return setProperty(acmType, value, true, 0);
    }

    public <T extends Serializable> boolean setProperty(String acmType, T value, boolean cacheOkay,
        // count prevents inf
        // loop
        int count) {
        if (logger.isDebugEnabled())
            logger.debug("setProperty(acmType=" + acmType + ", value=" + value + ")");
        boolean success = true;
        if (useFoundationalApi) {
            try {
                makeSureNodeRefIsNotFrozen();
                transactionCheck();
                services.getNodeService().setProperty(nodeRef, createQName(acmType), value);
                if (cacheOkay)
                    NodeUtil.propertyCachePut(getNodeRef(), acmType, value);
                if (acmType.equals(Acm.ACM_NAME)) {
                    renamed = true;
                }
            } catch (Exception e) {
                // This should never happen!
                success = false;
                // If the node is a version, then we will catch an exception.
                // Try again with the live node, but make sure it's the latest
                // version.
                NodeRef liveRef = nodeRef;
                if (isAVersion()) {
                    success = true;
                    logger.error(
                        "Tried to set property of a version nodeRef in " + "setProperty(acmType=" + acmType + ", value="
                            + value + ") for EmsScriptNode " + this + " calling NodeService.setProperty(nodeRef="
                            + nodeRef + ", " + acmType + ", " + value + ")");
                    if (count > 0) {
                        this.log("ERROR! Potential infinite recursion!");
                        return false;
                    }
                    liveRef = getLiveNodeRefFromVersion();
                    if (!nodeRef.equals(liveRef)) {
                        // make sure the version is equal or greater
                        int comp = NodeUtil.compareVersions(nodeRef, liveRef);
                        if (comp > 0) {
                            logger.error("ERROR! Live version " + liveRef + "" + " is earlier than versioned ref "
                                + "when trying to set property of a version nodeRef in " + "setProperty(acmType="
                                + acmType + ", value=" + value + ") for EmsScriptNode " + this
                                + " calling NodeService.setProperty(nodeRef=" + nodeRef + ", " + acmType + ", " + value
                                + ")");
                            success = false;
                        } else if (comp < 0) {
                            logger.error("WARNING! Versioned node ref is not most current "
                                + "when trying to set property of a version nodeRef in " + "setProperty(acmType="
                                + acmType + ", value=" + value + ") for EmsScriptNode " + this
                                + " calling NodeService.setProperty(nodeRef=" + nodeRef + ", " + acmType + ", " + value
                                + ")" + ".\nWARNING! Setting property using live node ref " + liveRef
                                + "last modified at " + NodeUtil.getLastModified(liveRef));
                        }
                        nodeRef = liveRef; // this is
                        if (comp <= 0) {
                            liveRef = null;
                            success = setProperty(acmType, value, cacheOkay, count + 1);
                            if (cacheOkay)
                                NodeUtil.propertyCachePut(getNodeRef(), acmType, value);
                            success = true;
                        }
                    }
                }
                if (nodeRef.equals(liveRef)) {
                    logger.error("Got exception in " + "setProperty(acmType=" + acmType + ", value=" + value
                        + ") for EmsScriptNode " + this + " calling NodeService.setProperty(nodeRef=" + nodeRef + ", "
                        + acmType + ", " + value + ")");
                    e.printStackTrace();
                    // StackTraceElement[] trace = e.getStackTrace();
                    // StackTraceElement s = trace[0];
                    // s.getMethodName()
                }
            }
        } else {
            makeSureNodeRefIsNotFrozen();
            transactionCheck();
            getProperties().put(acmType, value);
            save();
            if (cacheOkay)
                NodeUtil.propertyCachePut(getNodeRef(), acmType, value);
            if (acmType.equals(Acm.ACM_NAME)) {
                renamed = true;
                // removeChildrenFromJsonCache();
            }
        }
        return success;
    }

    public void setResponse(StringBuffer response) {
        this.response = response;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Checks whether user has permissions to the node and logs results and status as appropriate
     *
     * @param permissions Permissions to check
     * @return true if user has specified permissions to node, false otherwise
     */
    public boolean checkPermissions(String permissions) {
        return checkPermissions(permissions, null, null);
    }

    /**
     * Checks whether the user making the web request (not the run-as user) has permissions to the
     * node and logs results and status as appropriate. If a response object is supplied, a warning
     * is generated when the user does not have the permission.
     *
     * @param permissions Permissions to check
     * @param response
     * @param status
     * @return true if user has specified permissions to node, false otherwise
     */
    public boolean checkPermissions(String permissions, StringBuffer response, Status status) {
        if (!hasPermission(permissions)) {
            if (response != null) {

                // Assume admin role to make sure getProperty() doesn't fail
                // because of permissions.
                String runAsUser = AuthenticationUtil.getRunAsUser();
                boolean changeUser = !ADMIN_USER_NAME.equals(runAsUser);
                if (changeUser) {
                    AuthenticationUtil.setRunAsUser(ADMIN_USER_NAME);
                }

                // Get sysmlid
                Object property = getProperty(Acm.ACM_ID);
                if (property == null) {
                    property = getProperty(Acm.CM_NAME);
                }

                // Return to original running user.
                if (changeUser) {
                    AuthenticationUtil.setRunAsUser(runAsUser);
                }

                // Log warning for missing permissions.
                if (property != null) {
                    String msg = String.format("Warning! No %s privileges to sysmlid: %s.", permissions.toUpperCase(),
                        property.toString());
                    response.append(msg);
                    if (status != null) {
                        status.setCode(HttpServletResponse.SC_FORBIDDEN, msg);
                    }
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Checks whether the user making the web request (not the run-as user) has permissions to the
     * node and logs results and status as appropriate.
     *
     * @param permission the permission to check
     * @return whether the user has the permission for this node
     * @see org.alfresco.repo.jscript.ScriptNode#hasPermission(java.lang.String)
     */
    @Override public boolean hasPermission(String permission) {
        String realUser = AuthenticationUtil.getFullyAuthenticatedUser();
        String runAsUser = AuthenticationUtil.getRunAsUser();
        boolean changeUser = !realUser.equals(runAsUser);
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(realUser);
        }
        boolean b = super.hasPermission(permission);
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(runAsUser);
        }
        return b;
    }

    /**
     * Override equals for EmsScriptNodes
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override public boolean equals(Object obj) {
        return equals(obj, true);
    }

    /**
     * @return the head or current version of the node ref if it exists; otherwise return the
     * existing node ref
     */
    public NodeRef normalizedNodeRef() {
        VersionService vs = getServices().getVersionService();
        Version thisHeadVersion = this.getHeadVersion();
        NodeRef thisCurrent = thisHeadVersion == null ? null : thisHeadVersion.getVersionedNodeRef();
        if (thisCurrent == null) {
            Version thisCurrentVersion = vs.getCurrentVersion(this.nodeRef);
            thisCurrent = thisCurrentVersion == null ? null : thisCurrentVersion.getVersionedNodeRef();
        }
        if (thisCurrent == null)
            return nodeRef;
        return thisCurrent;
    }

    /**
     * Check to see if the nodes are the same or (if tryCurrentVersions is true) if their
     * currentVersions are the same.
     *
     * @param obj
     * @param tryCurrentVersions
     * @return true iff equal
     */
    public boolean equals(Object obj, boolean tryCurrentVersions) {

        if (!(obj instanceof EmsScriptNode))
            return false;
        EmsScriptNode that = (EmsScriptNode) obj;
        boolean same = this.nodeRef.equals(that.nodeRef);
        if (same || !tryCurrentVersions)
            return same;

        // See if they are different versions of the same node.
        VersionService vs = getServices().getVersionService();
        boolean isThisV = vs.isAVersion(this.nodeRef);
        boolean isThatV = vs.isAVersion(that.nodeRef);
        if (!isThisV && !isThatV)
            return same;
        NodeRef thisCurrent = this.normalizedNodeRef();
        NodeRef thatCurrent = that.normalizedNodeRef();
        if (thisCurrent == thatCurrent)
            return true;
        if (thisCurrent == null || thatCurrent == null)
            return false;
        return thisCurrent.equals(thatCurrent);
    }

    /**
     * Override exists for EmsScriptNodes
     *
     * @see org.alfresco.repo.jscript.ScriptNode#exists()
     */
    @Override public boolean exists() {
        return exists(false);
    }

    public boolean exists(boolean includeDeleted) {
        if (!scriptNodeExists())
            return false;
        return !(!includeDeleted && hasAspect("ems:Deleted"));
    }

    public boolean scriptNodeExists() {
        return super.exists();
    }

    public boolean isDeleted() {
        if (super.exists()) {
            return hasAspect("ems:Deleted");
        }
        return false;
    }

    @Override public int compare(EmsScriptNode arg0, EmsScriptNode arg1) {
        if (arg0 == arg1)
            return 0;
        if (arg0 == null)
            return -1;
        else if (arg1 == null)
            return 1;
        return arg0.getNodeRef().getId().compareTo(arg1.getNodeRef().getId());
    }

    @Override public int compareTo(EmsScriptNode o) {
        return this.compare(this, o);
    }

    public ServiceRegistry getServices() {
        return services;
    }

    public EmsScriptNode(NodeRef nodeRef, ServiceRegistry services) {
        super(nodeRef, services);
    }

    @Override public Set<QName> getAspectsSet() {
        String runAsUser = AuthenticationUtil.getRunAsUser();
        boolean changeUser = !ADMIN_USER_NAME.equals(runAsUser);
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(ADMIN_USER_NAME);
        }
        Set<QName> set = super.getAspectsSet();
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(runAsUser);
        }
        return set;
    }

    /**************************
     * Miscellaneous functions
     **************************/
    public Version getHeadVersion() {
        VersionService vs = getServices().getVersionService();
        Version headVersion = null;
        if (getIsVersioned()) {
            VersionHistory history = vs.getVersionHistory(this.nodeRef);
            if (history != null) {
                headVersion = history.getHeadVersion();
            }
        }
        return headVersion;
    }

    @Override public boolean removeAspect(String type) {
        if (hasAspect(type)) {
            makeSureNodeRefIsNotFrozen();
            transactionCheck();

            updateBogusProperty(type);

            return super.removeAspect(type);
        }
        return true;
    }

    public static EmsScriptNode getSiteNode(String sysmlid) {
        if (sysmlid == null) {
            return null;
        }
        String runAsUser = AuthenticationUtil.getRunAsUser();
        boolean changeUser = !ADMIN_USER_NAME.equals(runAsUser);
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(ADMIN_USER_NAME);
        }
        ServiceRegistry services = NodeUtil.getServiceRegistry();
        SiteInfo si = services.getSiteService().getSite(sysmlid);
        if (si != null) {
            EmsScriptNode site = new EmsScriptNode(si.getNodeRef(), services, null);
            if (changeUser) {
                AuthenticationUtil.setRunAsUser(runAsUser);
            }
            return site;
        }
        if (changeUser) {
            AuthenticationUtil.setRunAsUser(runAsUser);
        }
        return null;
    }

    public void delete() {
        if (!checkPermissions(PermissionService.WRITE, getResponse(), getStatus())) {
            log("no write permissions to delete workpsace " + getName());
            return;
        }

        // Add the delete aspect to mark as "deleted"
        makeSureNodeRefIsNotFrozen();
        addAspect("ems:Deleted");
    }

}
