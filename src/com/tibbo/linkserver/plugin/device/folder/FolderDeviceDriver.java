        package com.tibbo.linkserver.plugin.device.folder;

import com.tibbo.aggregate.common.Log;
import com.tibbo.aggregate.common.context.*;
import com.tibbo.aggregate.common.datatable.*;
import com.tibbo.aggregate.common.device.AbstractDeviceDriver;
import com.tibbo.aggregate.common.device.DeviceContext;
import com.tibbo.aggregate.common.device.DeviceEntities;
import com.tibbo.aggregate.common.device.DeviceException;
import com.tibbo.aggregate.common.device.DisconnectionException;
import com.tibbo.aggregate.common.security.ServerPermissionChecker;
import com.tibbo.aggregate.common.util.AggreGateThread;
import com.tibbo.aggregate.common.util.ThreadManager;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Юрий
 */
public class FolderDeviceDriver extends AbstractDeviceDriver {

    private ArrayList<VarList> sVar;
    public Socket sDevOut = null;
    public Socket sDevOutRez = null;
    private byte[] buffer = null;
    private Integer Count = 0;
    private byte IDpocket;
    private byte IDblock;
    private Context device = null;

    public FolderDeviceDriver() {
        super("ssddrv", VFT_CONNECTION_PROPERTIES);
    }

    @Override
    public void setupDeviceContext(DeviceContext deviceContext)
            throws ContextException {
        super.setupDeviceContext(deviceContext);
        deviceContext.setDefaultSynchronizationPeriod(1000L);
        VariableDefinition vd = new VariableDefinition("connectionProperties", VFT_CONNECTION_PROPERTIES, true, true, "connectionProperties", ContextUtils.GROUP_ACCESS);
        vd.setIconId("var_connection");
        vd.setHelpId("ls_drivers_SSD");
        vd.setWritePermissions(ServerPermissionChecker.getManagerPermissions());
        deviceContext.addVariableDefinition(vd);

        deviceContext.setDeviceType("ssddrv");
    }
    private static final String VF_IP = "IPaddr";
    private static final String VF_PORT = "port";
    private static final String VF_DEVICE = "device";
    private static final String VF_COUNT = "Count";
    private static final String VF_IDPOCKET = "IDpocket";
    private static final String VF_IDBLOCK = "IDblock";
    private static final String VF_IPR = "IPaddrRez";
    private static final String VF_PORTR = "portRez";

    @Override
    public List<VariableDefinition> readVariableDefinitions(DeviceEntities entities) throws ContextException, DeviceException, DisconnectionException {
        List<VariableDefinition> rez = new LinkedList();
        VariableDefinition vardef = new VariableDefinition(VF_COUNT, VFT_VARIABLE_DEFINITIONS, true, true, "Циклический счетчик пакетов", ContextUtils.GROUP_REMOTE);
        rez.add(vardef);
        //Log.CORE.info("readVariableDefinitions Ok ");
        return rez;
    }

    @Override
    public DataTable readVariableValue(VariableDefinition vd, CallerController caller) throws ContextException, DeviceException, DisconnectionException {
        DataTable rez = new DataTable(vd.getFormat());
        rez.addRecord(Count);
        //Log.CORE.info("readVariableValue Ok ");
        return rez;
    }

    @Override
    public void writeVariableValue(VariableDefinition vd, CallerController caller, DataTable value, DataTable deviceValue)
            throws ContextException, DeviceException, DisconnectionException {
        if (!vd.getName().equals(VF_COUNT)) {
            return;
        }
        //Log.CORE.info("writeVariableValue entry ");
        Count = value.rec().getInt(VF_COUNT);
        if (Count > 65535) {
            Count = 0;
        }
        makeBuffer();
        //Log.CORE.info("writeVariableValue makeBuffer ok ");
        sendBuffer();
        //Log.CORE.info("writeVariableValue sendBuffer ok ");

    }
    public String ip1;
    public int port1;
    public String ip2;
    public int port2;

    @Override
    public void connect() throws DeviceException {
        DataRecord connProps = null;
        try {
            connProps = getDeviceContext().getVariable("connectionProperties", getDeviceContext().getCallerController()).rec();
        } catch (ContextException ex) {
            throw new DeviceException("connectionProperties not found " + ex.getMessage());
        }
        String IPaddr = connProps.getString(VF_IP);
        Integer port = connProps.getInt(VF_PORT);
        
        ip1=IPaddr;
        port1=port;
        
        String NameDevice = connProps.getString(VF_DEVICE);
        IDpocket = (byte) (connProps.getInt(VF_IDPOCKET) & 0xff);
        IDblock = (byte) (connProps.getInt(VF_IDBLOCK) & 0xff);
        ContextManager cm = getDeviceContext().getContextManager();
        if (cm == null) {
            throw new DeviceException("Dont open  ContextManager " + NameDevice);
        }

        device = cm.get(NameDevice, getDeviceContext().getCallerController());
        if (device == null) {
            throw new DeviceException("Dont open  device " + NameDevice);
        }
        DataTable reg;
        try {
            reg = device.getVariable("SSD", getDeviceContext().getCallerController());
        } catch (ContextException ex) {
            throw new DeviceException("Dont open table registers on device " + NameDevice);
        }
        sVar = new ArrayList<>();
        buffer = null;
        for (DataRecord rec : reg) {
            String name = rec.getString("name");
            int format = rec.getInt("format");
            int type = rec.getInt("type");
            sVar.add(new VarList(name, type, format));
        }
        try {
            sDevOut = new Socket(IPaddr, port);
        } catch (IOException ex) {
            sDevOut = null;
            throw new DeviceException("Dont open socket " + IPaddr + ":" + port.toString() + " " + ex.toString());
        }
        IPaddr = connProps.getString(VF_IPR);
        port = connProps.getInt(VF_PORTR);
        
        ip2=IPaddr;
        port2=port;
        
        try {
            sDevOutRez = new Socket(IPaddr, port);
        } catch (IOException ex) {
            sDevOutRez = null;
            throw new DeviceException("Dont open socket " + IPaddr + ":" + port.toString() + " " + ex.toString());
        }

        buffer = new byte[22 + (sVar.size() * 6)];
                if (thRead == null) {
            thRead = new SSDReconect(this, thrManager);
        }

        super.connect();

    }
    
    private ThreadManager thrManager = new ThreadManager();
    private SSDReconect thRead = null;

    @Override
    public void disconnect() throws DeviceException {
        if ((sDevOut != null)||(sDevOutRez != null)) {
            try {
                if (sDevOut!=null )sDevOut.close();
                if (sDevOutRez!=null )sDevOutRez.close();
            } catch (IOException ex) {
                sDevOut = null;
                sDevOutRez=null;
                throw new DeviceException("Dont close socket " + ex.toString());
            }
        }
        sDevOut = null;
        sDevOutRez = null;
        buffer = null;
        super.disconnect(); //To change body of generated methods, choose Tools | Templates.
    }

    private static final TableFormat VFT_CONNECTION_PROPERTIES;
    private static final TableFormat VFT_VARIABLE_DEFINITIONS;

    static {
        VFT_CONNECTION_PROPERTIES = new TableFormat(1, 1);

        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<IPaddr><S><D=").append("IP address программы связи").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<port><I><A=502><D=").append("Номер порта программы связи").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<IPaddrRez><S><D=").append("Резервный IP address программы связи").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<portRez><I><A=502><D=").append("Резервный Номер порта программы связи").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<device><S><D=").append("Устройство с информацией БРМ").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<IDpocket><I><A=65><D=").append("ID пакетов ").append(">").toString()));
        VFT_CONNECTION_PROPERTIES.addField(FieldFormat.create((new StringBuilder()).append("<IDblock><I><A=1><D=").append("ID энергоблока ").append(">").toString()));

        VFT_VARIABLE_DEFINITIONS = new TableFormat(1, 1);
        VFT_VARIABLE_DEFINITIONS.addField(FieldFormat.create((new StringBuilder()).append("<Count><I><A=0><D=").append("Циклический счеьчик пакетов").append(">").toString()));
        VFT_VARIABLE_DEFINITIONS.setMinRecords(1);
        VFT_VARIABLE_DEFINITIONS.setMaxRecords(1);
        VFT_VARIABLE_DEFINITIONS.setUnresizable(true);

    }

    private void makeBuffer() throws DeviceException {
        if (buffer == null) {
            return;
        }
        buffer[0] = IDpocket;
        buffer[1] = IDblock;
        byte[] b = intToByte(Count);
        buffer[2] = b[0];
        buffer[3] = b[1];
        b = intToByte(buffer.length);
        buffer[4] = b[0];
        buffer[5] = b[1];
        makeTimePeek();
        int i = 22;
        for (VarList var : sVar) {
            byte[] bq = new byte[2];
            byte[] bv = new byte[4];

            DataTable v;
            try {
                v = device.getVariable(var.name, getDeviceContext().getCallerController());
            } catch (ContextException ex) {
                v = null;
            }
            if (v == null) {
                byte[] bb = intToByte(32768);
                buffer[i] = bb[0];
                buffer[i + 1] = bb[1];
            } else {
                byte[] bb;
                switch (var.format) {
                    case 0:
                        bb = intToByte(v.rec().getBoolean(0) ? 1 : 0);
                        break;
                    case 1:
                        bb = intToByte(v.rec().getInt(0));
                        break;
                    case 2:
                        bb = longToByte(v.rec().getLong(0));
                        break;

                    case 3:
                        bb = floatToBytes(v.rec().getFloat(0));
                        break;
                    default:
                        throw new DeviceException("Not supported type " + var.name);
                }
                buffer[i] = 0;
                buffer[i + 1] = 0;
                System.arraycopy(bb, 0, buffer, i + 2, 4);
                i += 6;
            }

        }
    }

    private void makeTimePeek() {
        byte[] b = new byte[16];
        int[] id = new int[8];
        Date date = new Date();
        Calendar dt = Calendar.getInstance();
        dt.setTime(date);
        id[0] = dt.get(Calendar.YEAR);
        id[1] = dt.get(Calendar.MONTH) + 1;
        id[2] = dt.get(Calendar.DAY_OF_WEEK);
        id[3] = dt.get(Calendar.DAY_OF_MONTH);
        id[4] = dt.get(Calendar.HOUR_OF_DAY);
        id[5] = dt.get(Calendar.MINUTE);
        id[6] = dt.get(Calendar.SECOND);
        id[7] = dt.get(Calendar.MILLISECOND);
        for (int i = 0; i < 8; i++) {
            byte[] bb = intToByte(id[i]);
            b[i * 2] = bb[0];
            b[(i * 2) + 1] = bb[1];
        }
        System.arraycopy(b, 0, buffer, 6, 16);
    }

    private static byte[] longToByte(long value) {
        return new byte[]{
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 24) & 0xff)};
    }

    private static byte[] intToByte(int value) {
        return new byte[]{
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 24) & 0xff)};
    }

    private byte[] floatToBytes(Float f) {
        int floatBits = Float.floatToIntBits(f);
        byte floatBytes[] = new byte[4];
        floatBytes[3] = (byte) ((floatBits >> 24) & 0xff);
        floatBytes[2] = (byte) ((floatBits >> 16) & 0xff);
        floatBytes[1] = (byte) ((floatBits >> 8) & 0xff);
        floatBytes[0] = (byte) ((floatBits) & 0xff);
        return floatBytes;
    }

    private void sendBuffer() throws DeviceException {
        if (buffer == null) {
            return;
        }
//        String s = "";
//        for (int i = 0; i < 60; i++) {
//            s += " " + Integer.toHexString(buffer[i]);
//        }
//        Log.CORE.info("Buffer= " + s);
        try {
            if (sDevOut != null) {
                sDevOut.getOutputStream().write(buffer, 0, buffer.length);
            }
            if (sDevOutRez != null) {
                sDevOutRez.getOutputStream().write(buffer, 0, buffer.length);

            }
        } catch (IOException ex) {
            throw new DeviceException("Socket error " + ex.toString());
        }
    }
    class SSDReconect extends AggreGateThread {

        private FolderDeviceDriver fd;
        ThreadManager threadManager = null;

        public SSDReconect(FolderDeviceDriver fd, ThreadManager threadManager) {
            super(threadManager);
            this.threadManager = threadManager;
            threadManager.addThread(this);
            this.fd = fd;
            start();
        }

        @Override
        public synchronized void start() {
            while(!isInterrupted())
            {
                try {
                    if(!fd.isConnected()) return;
                    AggreGateThread.sleep(60000);
                    if(fd.sDevOut==null){
                        fd.sDevOut = new Socket(fd.ip1, fd.port1);
                    }
                    if(fd.sDevOutRez==null){
                        fd.sDevOutRez = new Socket(fd.ip2, fd.port2);
                    }
                } catch (InterruptedException|IOException ex) {
                    Log.CORE.info("Связь SSD"+ex.getMessage());
                } 

            }
        }
    }
}

