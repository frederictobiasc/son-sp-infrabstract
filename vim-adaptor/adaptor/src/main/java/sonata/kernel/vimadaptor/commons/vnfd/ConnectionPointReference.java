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
 * @author Dario Valocchi (Ph.D.), UCL
 *
 */

package sonata.kernel.vimadaptor.commons.vnfd;

import com.fasterxml.jackson.annotation.JsonProperty;
import javassist.NotFoundException;

public class ConnectionPointReference implements Comparable<ConnectionPointReference> {

    @JsonProperty("connection_point_ref")
    private String connectionPointRef;
    private int position;
    private enum ReferencePart { VNF_ID, CONNECTION_POINT_NAME}

    @Override
    public int compareTo(ConnectionPointReference o) {
        return (int) Math.signum(position - o.getPosition());
    }

    public String getConnectionPointRef() {
        return connectionPointRef;
    }

    public int getPosition() {
        return position;
    }

    public void setConnectionPointRef(String connectionPointRef) {
        this.connectionPointRef = connectionPointRef;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "[" + connectionPointRef + "," + position + "]";
    }

    public String getVnfId() {
        if (checkAndExtract(ReferencePart.VNF_ID) != null) {
            return checkAndExtract(ReferencePart.VNF_ID);
        } return null;
    }

    public String getConnectionPointReferenceName() {
        if (checkAndExtract(ReferencePart.CONNECTION_POINT_NAME) != null) {
            return checkAndExtract(ReferencePart.CONNECTION_POINT_NAME);
        } return null;
    }

    private String checkAndExtract(ReferencePart referencePart) {
        if (getConnectionPointRef().contains(":") && getConnectionPointRef().split(":").length == 2) {
            switch (referencePart) {
                case VNF_ID:
                    return getConnectionPointRef().split(":")[0];
                case CONNECTION_POINT_NAME:
                    return getConnectionPointRef().split(":")[1];
            }
        }
        return null;

    }
}
