/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tibbo.linkserver.plugin.device.folder;

/**
 *
 * @author Юрий
 */
public class VarList
{
    public String name;
    public int format;  
    public VarList(String name,int type,int format)
    {
        this.name=name;
        if((type==0)||(type==1)) {this.format=0; return;}
        if(format<4){this.format=1; return;}
        if(format<8){this.format=2; return;}
        if(format<11){this.format=3; return;}
        if(format<14){this.format=4; return;}
        if(format<16){this.format=5; return;}
        this.format=-1;
    }
}
