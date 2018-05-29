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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;
import sonata.kernel.vimadaptor.commons.NetworkConfigurePayload;
import sonata.kernel.vimadaptor.commons.nsd.NetworkFunction;
import sonata.kernel.vimadaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.vimadaptor.commons.nsd.VirtualLink;
import sonata.kernel.vimadaptor.commons.vnfd.VirtualDeploymentUnit;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.vimadaptor.wrapper.NetworkWrapper;
import sonata.kernel.vimadaptor.wrapper.WrapperConfiguration;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VIMEmuNetworkWrapper extends NetworkWrapper {
    private static final String logName = "[VIMEmuNetworkWrapper] ";
    private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ObjectMapper mapper;

    public VIMEmuNetworkWrapper(WrapperConfiguration config) {
        super(config);
        System.out.println(logName + "configureNetworking called");
        mapper = new ObjectMapper();
    }


    /**
     * Relies on assumptions:
     * - Every VNF contains one VDU
     * - Forwarding path is equal to ConnectionPoints in NSD (forwarding path is not considered at all)
     * - Format of VDU ConnectionPoints (vdu:interface)
     * This functionality is developed around https://github.com/sonata-nfv/son-examples/tree/
     * 5338554f36d014ee5f339261b05c54f94249180e/service-projects/sonata-fw-vtc-service-emu
     *
     * @see sonata.kernel.vimadaptor.wrapper.NetworkWrapper#configureNetworking(NetworkConfigurePayload)
     */
    @Override
    public void configureNetworking(NetworkConfigurePayload data) throws Exception {
        Logger.info(logName + "configureNetworking called");
        if (data.getNsd().getVirtualLinks().isEmpty()) {
            throw new IllegalArgumentException("NSD contains no virtual links for deployment");
        }
        List<VIMEmuNetworkPayload> networkPayloads = translateToVIMEmuNetworkPayload(translateVnfIdToVnfName(extractContainerInterfaces(data.getNsd().getVirtualLinks()), data.getNsd()), data);

        for (VIMEmuNetworkPayload chainPayload : networkPayloads) {
            deploySFCOnVIM(mapper.writeValueAsString(chainPayload));
        }
    }

    private void deploySFCOnVIM(String chainDeployJSON) {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPut httpPut = new HttpPut("http://127.0.0.1:5001/restapi/network");
        httpPut.addHeader("Content-Type", "application/json");
        try {
            httpPut.setEntity(new StringEntity(chainDeployJSON));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        HttpResponse response = null;
        try {
            response = httpClient.execute(httpPut);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String rawAnswer = null;
        try {
            rawAnswer = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Logger.info(rawAnswer);
    }

    private Map<String, String> translateVnfIdToVnfName(Map<String, String> vnfNameRawInterfacesMap, ServiceDescriptor nsd) {
        Map<String, String> vnfNameInterfacesMap = new HashMap<>();
        for (Map.Entry<String, String> vnfNameRawInterface : vnfNameRawInterfacesMap.entrySet()) {
            String newKey = null, newValue = null;
            for (NetworkFunction networkFunction : nsd.getNetworkFunctions()) {
                if (vnfNameRawInterface.getValue().split(":")[0].equals(networkFunction.getVnfId())) {
                    newValue = networkFunction.getVnfName() + ":" + vnfNameRawInterface.getValue().split(":")[1];
                }
                if (vnfNameRawInterface.getKey().split(":")[0].equals(networkFunction.getVnfId())) {
                    newKey = networkFunction.getVnfName() + ":" + vnfNameRawInterface.getKey().split(":")[1];
                }
            }
            if (newKey != null && newValue != null) {
                vnfNameInterfacesMap.put(newKey, newValue);
            } else {
                throw new IllegalArgumentException("Inconsistency in NSD: VnfName in Virtuallinks does not match definition in networkFunctions");
            }
        }
        return vnfNameInterfacesMap;
    }


    /**
     * Vnfds contain connection between virtual connectionPoints and connectionPoints of Vdus.
     *
     * @param
     * @param
     * @return
     */
    private List<VIMEmuNetworkPayload> translateToVIMEmuNetworkPayload(Map<String, String> rawVduInterfacePairs, NetworkConfigurePayload data) {
        ArrayList<VIMEmuNetworkPayload> vimEmuNetworkPayloads = new ArrayList<>();
        for (Map.Entry<String, String> rawVduInterfacePair : rawVduInterfacePairs.entrySet()) {
            VIMEmuNetworkPayload networkPayload = new VIMEmuNetworkPayload();
            networkPayload.setSourceVnfName(rawVduInterfacePair.getKey().split(":")[0]);
            networkPayload.setSourceVnfInterface(rawVduInterfacePair.getKey().split(":")[1]);
            networkPayload.setDestinationVnfName(rawVduInterfacePair.getValue().split(":")[0]);
            networkPayload.setDestinationVnfInterface(rawVduInterfacePair.getValue().split(":")[1]);
            networkPayload.setSourceVduId(
                    Common.translateToVimVduId(
                            networkPayload.getSourceVnfName(),
                            getCorrespondingVdu(
                                    networkPayload.getSourceVnfName(), data
                            ).getId()
                    )
            );
            networkPayload.setSourceVduInterface(
                    Common.translateToVimVduNetworkInterface(
                            networkPayload.getSourceVduId(),
                            getCorrespondingVduInterface(
                                    networkPayload.getSourceVnfName(),
                                    networkPayload.getSourceVnfInterface(),
                                    networkPayload.getSourceVduId(),
                                    data
                            )
                    )
            );

            networkPayload.setDestinationVduId(
                    Common.translateToVimVduId(
                            networkPayload.getDestinationVnfName(), getCorrespondingVdu(
                                    networkPayload.getDestinationVnfName(), data).getId()
                    )
            );
            networkPayload.setDestinationVduInterface(
                    Common.translateToVimVduNetworkInterface(
                            networkPayload.getDestinationVduId(),
                            getCorrespondingVduInterface(
                                    networkPayload.getDestinationVnfName(),
                                    networkPayload.getDestinationVnfInterface(),
                                    networkPayload.getDestinationVduId(), data
                            )
                    )
            );
            vimEmuNetworkPayloads.add(networkPayload);
        }

        return vimEmuNetworkPayloads;

    }

    private String getCorrespondingVduInterface(String vnfName, String vnfInterface, String vduId, NetworkConfigurePayload data) {
        for (VnfVirtualLink virtualLink : getCorrespondingVnfd(vnfName, data).getVirtualLinks()) {
            if (virtualLink.getConnectivityType() == VirtualLink.ConnectivityType.E_LINE) {
                if (virtualLink.getConnectionPointsReference().contains(vnfInterface)) {
                    if (virtualLink.getConnectionPointsReference().get(0).equals(vnfInterface)) {
                        return virtualLink.getConnectionPointsReference().get(1).split(":")[1];
                    }
                    if (virtualLink.getConnectionPointsReference().get(1).equals(vnfInterface)) {
                        return virtualLink.getConnectionPointsReference().get(0).split(":")[1];
                    }
                }
            }
        }

        throw new IllegalArgumentException("Could not find corresponding vduInterface for \"" + vnfName + ":" + vnfInterface + "\"");
    }

    private VirtualDeploymentUnit getCorrespondingVdu(String sourceVnfName, NetworkConfigurePayload data) {
        return getCorrespondingVnfd(sourceVnfName, data).getVirtualDeploymentUnits().get(0);
    }

    private VnfDescriptor getCorrespondingVnfd(String sourceVnfName, NetworkConfigurePayload data) {
        for (VnfDescriptor vnfd : data.getVnfds()) {
            if (vnfd.getName().equals(sourceVnfName)) {
                return vnfd;
            }
        }
        throw new IllegalArgumentException("Could not find VNF for given argument\"" + sourceVnfName + "\"");
    }

    private Map<String, String> extractContainerInterfaces(ArrayList<VirtualLink> virtualLinks) {
        Map<String, String> containerInterfaces = new HashMap<>();

        for (VirtualLink virtualLink : virtualLinks) {
            if (virtualLink.getConnectivityType() == VirtualLink.ConnectivityType.E_LINE && assertOnlyContainerInterfaces(virtualLink)) {
                containerInterfaces.put(virtualLink.getConnectionPointsReference().get(0), virtualLink.getConnectionPointsReference().get(1));
            }
        }
        return containerInterfaces;
    }

    private boolean assertOnlyContainerInterfaces(VirtualLink virtualLink) {
        if (virtualLink.getConnectionPointsReference().size() != 2) {
            return false;
        }
        for (String connectionPointReference : virtualLink.getConnectionPointsReference()) {
            if (connectionPointReference.split(":").length != 2) {
                return false;
            }
        }
        return true;
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