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
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.LoggerFactory;
import sonata.kernel.vimadaptor.commons.*;
import sonata.kernel.vimadaptor.commons.nsd.NetworkFunction;
import sonata.kernel.vimadaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.vimadaptor.commons.nsd.VirtualLink;
import sonata.kernel.vimadaptor.commons.vnfd.ConnectionPointReference;
import sonata.kernel.vimadaptor.commons.vnfd.VirtualDeploymentUnit;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.vimadaptor.wrapper.NetworkWrapper;
import sonata.kernel.vimadaptor.wrapper.WrapperConfiguration;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.util.*;

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






        /*

        ArrayList<ConnectionPointReference> connectionPoints = data.getNsd().getForwardingGraphs().get(0).getNetworkForwardingPaths().get(0).getConnectionPoints();
        Collections.sort(connectionPoints);
        int portIndex = 0;

        ArrayList<OrderedMacAddress> macAddresses = new ArrayList<>();


        for (ConnectionPointReference connectionPointReference : connectionPoints) {
            ConnectionPointRecord matchingConnectionPointRecord = null;
            VnfDescriptor vnfd = NetworkFunctionHelper.getVnfdByName(data.getVnfds(),
                    NetworkFunctionHelper.getVnfNameById(data.getNsd().getNetworkFunctions(),
                            connectionPointReference.getVnfId()));
            VnfRecord vnfr = NetworkFunctionHelper.getVnfrByVnfdReference(data.getVnfrs(), vnfd.getUuid());
            VnfVirtualLink inputLink = getInputLink(connectionPointReference, vnfd);
            String vnfConnectionPointReference = getVnfConnectionPointReference(connectionPointReference, inputLink);

            Logger.debug("Searching for CpRecord of Cp: " + vnfConnectionPointReference);

            String vcId = null;
            String[] split = vnfConnectionPointReference.split(":");
            String vduId = split[0];
            String vnfConnectionConnectionPointName = split[1];
            if (split.length != 2) {
                throw new Exception(
                        "Illegal Format: A connection point reference should be in the format vdu_id:cp_name. Found: "
                                + vnfConnectionPointReference);
            }

            for (VduRecord vdu : vnfr.getVirtualDeploymentUnits()) {
                if (vdu.getId().equals(vduId)) {
                    for (VnfcInstance vnfc : vdu.getVnfcInstance()) {
                        for (ConnectionPointRecord cpRec : vnfc.getConnectionPoints()) {
                            Logger.debug("Checking " + cpRec.getId());
                            if (vnfConnectionConnectionPointName.equals(cpRec.getId())) {
                                matchingConnectionPointRecord = cpRec;
                                vcId = vnfc.getVcId();
                                break;
                            }
                        }
                    }
                }
            }

            String qualifiedName = NetworkFunctionHelper.getVnfNameById(data.getNsd().getNetworkFunctions(), connectionPointReference.getVnfId()) + "." + vnfConnectionPointReference + "." + data.getNsd().getInstanceUuid();
            // HeatPort connectedPort = null;
            // for (HeatPort port : composition.getPorts()) {
            // if (port.getPortName().equals(qualifiedName)) {
            // connectedPort = port;
            // break;
            // }
            // }
            if (matchingConnectionPointRecord == null) {
                throw new Exception(
                        "Illegal Format: cannot find the VNFR.VDU.VNFC.CPR matching: " + vnfConnectionPointReference);
            } else {
                // Eureka! What?
                OrderedMacAddress mac = new OrderedMacAddress();
                mac.setMac(matchingConnectionPointRecord.getInterface().getHardwareAddress());
                mac.setPosition(portIndex);
                mac.setVcId(vcId);
                mac.setReferenceCp(qualifiedName);
                portIndex++;
                macAddresses.add(mac);
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


        Collections.sort(macAddresses);
        int ruleNumber = 0;
        for (NapObject inNap : data.getNap().getIngresses()) {
            for (NapObject outNap : data.getNap().getEgresses()) {
                OvsPayload odlPayload = new OvsPayload("add", data.getServiceInstanceId() + "." + ruleNumber,
                        inNap.getNap(), outNap.getNap(), macAddresses);
                ObjectMapper mapper = new ObjectMapper(new JsonFactory());
                mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                // Logger.info(compositionString);
                String payload = mapper.writeValueAsString(odlPayload);
                Logger.debug(this.getConfig().getUuid() + " - " + this.getConfig().getVimEndpoint());
                Logger.debug(payload);
                /*{"action":"add","in_segment":"10.100.32.40/32","instance_id":"123.0","port_list":[
                {"port":"4e:41:8f:35:58:4a","order":0,
                "vc_id":"2661000e221aa15d8b19669c5031d4f3f91399a53ddb8f170940b974fd96b0a4"}
                ,{"port":"a6:00:87:3f:10:25","order":1,
                "vc_id":"2661000e221aa15d8b19669c5031d4f3f91399a53ddb8f170940b974fd96b0a4"}],
                "out_segment":"10.100.0.40/32"}

                InetAddress IPAddress = InetAddress.getByName(this.getConfig().getVimEndpoint());

                int sfcAgentPort = 55555;
                Socket clientSocket;// = new Socket(IPAddress, sfcAgentPort);
                clientSocket.setSoTimeout(10000);
                byte[] sendData = new byte[1024];
                sendData = payload.getBytes(Charset.forName("UTF-8"));
                PrintStream out = new PrintStream(clientSocket.getOutputStream());
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                out.write(sendData);
                out.flush();
                9
                String response;
                try {
                    response = in.readLine();
                } catch (SocketTimeoutException e) {
                    clientSocket.close();
                    Logger.error("Timeout exception from the OVS SFC agent");
                    throw new Exception("Request to OVS VIM agent timed out.");
                }
                if (response == null) {
                    in.close();
                    out.close();
                    clientSocket.close();
                    throw new Exception("null response received from OVS VIM ");
                }
                in.close();
                out.close();
                clientSocket.close();

                Logger.info("SFC Agent response:\n" + response);
                if (!response.equals("SUCCESS")) {
                    Logger.error("Unexpected response.");
                    Logger.error("received string length: " + response.length());
                    Logger.error("received string: " + response);
                    throw new Exception(
                            "Unexpected response from OVS SFC agent while trying to add a configuration.");
                }
                ruleNumber++;
            }
        }

        Logger.info("[OvsWrapper]networkConfigure-time: ");


        return;
        */
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
                                    networkPayload.getSourceVnfName(), data).getId()
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

    @NotNull
    private String getVnfConnectionPointReference(ConnectionPointReference connectionPointReference, VnfVirtualLink inputLink) throws Exception {
        String vnfConnectionPointReference = null;
        for (String cp : inputLink.getConnectionPointsReference()) {
            if (!cp.equals(connectionPointReference.getConnectionPointRef())) {
                vnfConnectionPointReference = cp;
                break;
            }
        }
        if (vnfConnectionPointReference == null) {
            throw new Exception(
                    "Illegal Format: Unable to find the VNFC Cp name connected to this in/out VNF VL");
        }
        return vnfConnectionPointReference;
    }

    @NotNull
    private VnfVirtualLink getInputLink(ConnectionPointReference connectionPointReference, VnfDescriptor vnfd) throws Exception {
        VnfVirtualLink inputLink = null;
        for (VnfVirtualLink link : vnfd.getVirtualLinks()) {
            if (link.getConnectionPointsReference().contains(connectionPointReference.getConnectionPointReferenceName())) {
                inputLink = link;
            }
        }
        if (inputLink == null) {
            for (VnfVirtualLink link : vnfd.getVirtualLinks()) {
                Logger.info(link.getConnectionPointsReference().toString());
            }
            throw new Exception(
                    "Illegal Format: unable to find the vnfd.VL connected to the VNFD.CP=" + connectionPointReference.getVnfId() + ":" + connectionPointReference.getConnectionPointRef());
        }
        if (inputLink.getConnectionPointsReference().size() != 2) {
            throw new Exception(
                    "Illegal Format: A vnf in/out vl should connect exactly two CPs. found: "
                            + inputLink.getConnectionPointsReference().size());
        }
        return inputLink;
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