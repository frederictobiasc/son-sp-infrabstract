/*
 * Copyright (c) 2015 SONATA-NFV, UCL, NOKIA, NCSR Demokritos ALL RIGHTS RESERVED.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Neither the name of the SONATA-NFV, UCL, NOKIA, NCSR Demokritos nor the names of its contributors
 * may be used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * This work has been performed in the framework of the SONATA project, funded by the European
 * Commission under Grant number 671517 through the Horizon 2020 and 5G-PPP programmes. The authors
 * would like to acknowledge the contributions of their colleagues of the SONATA partner consortium
 * (www.sonata-nfv.eu).
 *
 * @author Adel Zaalouk
 * @author Dario Valocchi (Ph.D.), UCL
 *
 */

package sonata.kernel.vimadaptor.wrapper.vimemu;

import org.slf4j.LoggerFactory;
import sonata.kernel.vimadaptor.commons.*;
import sonata.kernel.vimadaptor.commons.nsd.*;
import sonata.kernel.vimadaptor.commons.vnfd.ConnectionPointReference;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.vimadaptor.wrapper.NetworkWrapper;
import sonata.kernel.vimadaptor.wrapper.WrapperConfiguration;
import sonata.kernel.vimadaptor.wrapper.ovsWrapper.OrderedMacAddress;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class VIMEmuNetworkWrapper extends NetworkWrapper {
    private static final String logName = "[VIMEmuNetworkWrapper] ";
    private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(VIMEmuNetworkWrapper.class);
    private static final String ADAPTOR_SEGMENTS_CONF = "/adaptor/segments.conf";

    public VIMEmuNetworkWrapper(WrapperConfiguration config) {
        super(config);
        System.out.println(logName + "configureNetworking called");

    }

    /**
     * @see sonata.kernel.vimadaptor.wrapper.NetworkWrapper#configureNetworking(NetworkConfigurePayload)
     */
    @Override
    public void configureNetworking(NetworkConfigurePayload data) throws Exception {
        Logger.info(logName + "configureNetworking called");
        if (data.getNsd().getForwardingGraphs().size() <= 0) {
            throw new Exception("No Forwarding Graph specified in the descriptor");
        }
        long start = System.currentTimeMillis();

        String serviceInstanceId = data.getServiceInstanceId();
        ServiceDescriptor nsd = data.getNsd();
        ArrayList<VnfRecord> vnfrs = data.getVnfrs();
        ArrayList<VnfDescriptor> vnfds = data.getVnfds();
        ForwardingGraph graph = nsd.getForwardingGraphs().get(0);
        NetworkForwardingPath path = graph.getNetworkForwardingPaths().get(0);

        ArrayList<ConnectionPointReference> pathCp = path.getConnectionPoints();

        Collections.sort(pathCp);
        int portIndex = 0;

        ArrayList<OrderedMacAddress> odlList = new ArrayList<OrderedMacAddress>();
        // Pre-populate structures for efficent search.

        HashMap<String, String> vnfIdToNameMap = new HashMap<String, String>();

        for (NetworkFunction nf : nsd.getNetworkFunctions()) {
            vnfIdToNameMap.put(nf.getVnfId(), nf.getVnfName());
        }

        HashMap<String, VnfDescriptor> nameToVnfdMap = new HashMap<String, VnfDescriptor>();
        for (VnfDescriptor vnfd : vnfds) {
            nameToVnfdMap.put(vnfd.getName(), vnfd);
        }

        HashMap<String, VnfRecord> vnfdToVnfrMap = new HashMap<String, VnfRecord>();
        for (VnfRecord vnfr : vnfrs) {
            vnfdToVnfrMap.put(vnfr.getDescriptorReference(), vnfr);
        }

        for (ConnectionPointReference cpr : pathCp) {
            String name = cpr.getConnectionPointRef();
            if (!name.contains(":")) {
                continue;
            } else {
                String[] split = name.split(":");
                if (split.length != 2) {
                    throw new Exception(
                            "Illegal Format: A connection point reference should be in the format vnfId:CpName. It was "
                                    + name);
                }
                String vnfId = split[0];
                String cpRef = split[1];
                String vnfName = vnfIdToNameMap.get(vnfId);
                if (vnfName == null) {
                    throw new Exception("Illegal Format: Unable to bind vnfName to the vnfId: " + vnfId);
                }
                VnfDescriptor vnfd = nameToVnfdMap.get(vnfName);
                if (vnfd == null) {
                    throw new Exception("Illegal Format: Unable to bind VNFD to the vnfName: " + vnfName);
                }

                VnfRecord vnfr = vnfdToVnfrMap.get(vnfd.getUuid());
                if (vnfr == null) {
                    throw new Exception("Illegal Format: Unable to bind VNFD to the VNFR: " + vnfName);
                }

                VnfVirtualLink inputLink = null;
                for (VnfVirtualLink link : vnfd.getVirtualLinks()) {
                    if (link.getConnectionPointsReference().contains(cpRef)) {
                        inputLink = link;
                        break;
                    }
                }
                if (inputLink == null) {
                    for (VnfVirtualLink link : vnfd.getVirtualLinks()) {
                        Logger.info(link.getConnectionPointsReference().toString());
                    }
                    throw new Exception(
                            "Illegal Format: unable to find the vnfd.VL connected to the VNFD.CP=" + vnfId + ":" + cpRef);
                }

                if (inputLink.getConnectionPointsReference().size() != 2) {
                    throw new Exception(
                            "Illegal Format: A vnf in/out vl should connect exactly two CPs. found: "
                                    + inputLink.getConnectionPointsReference().size());
                }
                String vnfcCpReference = null;
                for (String cp : inputLink.getConnectionPointsReference()) {
                    if (!cp.equals(cpRef)) {
                        vnfcCpReference = cp;
                        break;
                    }
                }
                if (vnfcCpReference == null) {
                    throw new Exception(
                            "Illegal Format: Unable to find the VNFC Cp name connected to this in/out VNF VL");
                }

                Logger.debug("Searching for CpRecord of Cp: " + vnfcCpReference);
                ConnectionPointRecord matchingCpRec = null;
                String vcId = null;
                split = vnfcCpReference.split(":");
                String vduId = split[0];
                String vnfcCpName = split[1];
                if (split.length != 2) {
                    throw new Exception(
                            "Illegal Format: A VL connection point reference should be in the format vdu_id:cp_name. Found: "
                                    + vnfcCpReference);
                }

                for (VduRecord vdu : vnfr.getVirtualDeploymentUnits()) {
                    if (vdu.getId().equals(vduId)) {
                        for (VnfcInstance vnfc : vdu.getVnfcInstance()) {
                            for (ConnectionPointRecord cpRec : vnfc.getConnectionPoints()) {
                                Logger.debug("Checking " + cpRec.getId());
                                if (vnfcCpName.equals(cpRec.getId())) {
                                    matchingCpRec = cpRec;
                                    vcId = vnfc.getVcId();
                                    break;
                                }
                            }
                        }
                    }
                }

                String qualifiedName = vnfName + "." + vnfcCpReference + "." + nsd.getInstanceUuid();
                // HeatPort connectedPort = null;
                // for (HeatPort port : composition.getPorts()) {
                // if (port.getPortName().equals(qualifiedName)) {
                // connectedPort = port;
                // break;
                // }
                // }
                if (matchingCpRec == null) {
                    throw new Exception(
                            "Illegal Format: cannot find the VNFR.VDU.VNFC.CPR matching: " + vnfcCpReference);
                } else {
                    // Eureka!
                    OrderedMacAddress mac = new OrderedMacAddress();
                    mac.setMac(matchingCpRec.getInterface().getHardwareAddress());
                    mac.setPosition(portIndex);
                    mac.setVcId(vcId);
                    mac.setReferenceCp(qualifiedName);
                    portIndex++;
                    odlList.add(mac);
                }
            }
        }

        boolean nullNapCondition = data.getNap() == null
                || (data.getNap() != null
                && (data.getNap().getEgresses() == null || data.getNap().getIngresses() == null))
                || (data.getNap() != null && data.getNap().getEgresses() != null
                && data.getNap().getIngresses() != null && (data.getNap().getIngresses().size() == 0
                || data.getNap().getEgresses().size() == 0));
        if (nullNapCondition) {
            Logger.warn("NAP not specified, using default ones from default config file");
            Properties segments = new Properties();
            segments.load(new FileReader(new File(ADAPTOR_SEGMENTS_CONF)));
            NetworkAttachmentPoints nap = new NetworkAttachmentPoints();
            ArrayList<NapObject> ingresses = new ArrayList<NapObject>();
            ArrayList<NapObject> egresses = new ArrayList<NapObject>();
            ingresses.add(new NapObject("Athens", segments.getProperty("in")));
            egresses.add(new NapObject("Athens", segments.getProperty("out")));
            nap.setEgresses(egresses);
            nap.setIngresses(ingresses);
            data.setNap(nap);
        }
        return;
    }

    /**
     * @see sonata.kernel.vimadaptor.wrapper.NetworkWrapper#deconfigureNetworking(java.lang.String)
     */
    @Override
    public void deconfigureNetworking(String instanceId) {
        System.out.println(logName + "deconfigureNetworking called");
        return;
    }
}