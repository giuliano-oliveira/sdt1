package hangman;

import java.rmi.server.RemoteServer;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.net.InetAddress;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
public class Master extends RemoteServer implements HangmanMaster{
	boolean isJoining=false;
	HashMap<Byte[],Integer> slavesId;
	ArrayList<HangmanSlaveInfo> slaves;
	ArrayList<String> words;
	Server server;
	int maxLives;
	static class HeartBeatSlave implements Runnable{
        HangmanSlave server;
        Master obj;
        Byte[] clientIp;
        public HeartBeatSlave(Master o,HangmanSlave sv,Byte[] ip){
            server=sv;
            obj=o;
            clientIp=ip;
        }
        public void run(){
            while(true){
                try{Thread.sleep(500);}
                catch(Exception e){}
                try{
                    int n=server.beat();
                    if(n!=1)throw new Exception("Beat errado");
                }
                catch(Exception e){
                    Server.serverMsg("conexão perdida com um escravo ");
                    obj.kickSlave(clientIp);
                    return;
                }
            }
        }
    }
	int totalNoWords;
	static class HangmanSlaveInfo{
		HangmanSlave server;
		Registry registry;
	}
	public Master(ArrayList<String> w,int lives,Server s){
		server=s;
		totalNoWords=w.size();
		maxLives=lives;
		words=w;
		slavesId=new HashMap<Byte[],Integer>();
		slaves=new ArrayList<HangmanSlaveInfo>();
		// slavesNoWord=new HashMap<Byte[],Integer>();
	}
	public static Byte[] getAddr(String ip){
		byte[] in=new byte[0];
		try{in=InetAddress.getByName(ip).getAddress​();}
		catch(Exception e){
			//todo
		}
		Byte[] ans=new Byte[in.length];
		for(int i=0;i<in.length;i++){
			ans[i]=in[i];
		}
		return ans;
	}
	void kickSlave(Byte[] ip){
		int id=slavesId.get(ip);
		HangmanSlaveInfo hi=slaves.get(id);
		Server.serverMsg("Kickando escravo id:"+id);
		slavesId.remove(ip);
		slaves.remove(id);
		server.disassociateSlave(hi.server);
	}
	public void distributeWords()throws RemoteException{
		int expectedSize=totalNoWords/(slaves.size()+1);
		
		int ws=words.size();
		slaves.get(0).server.addWords(
			new ArrayList<String>(
				words.subList(expectedSize,ws)));
		words.subList(expectedSize,ws).clear();
		
		if(slaves.size()==1)return;
		//todo
		ArrayList<String> c=slaves.get(0).server.reduceWords(expectedSize);
		int s=slaves.size();
		int a=0;
		HangmanSlaveInfo si;
		for(int i=1;i<s;i++){
			si=slaves.get(i);
			a=si.server.addWords(c);
			if(i!=s-1)c=si.server.reduceWords(expectedSize);
		}
	}
	//rpc
	public void join() throws RemoteException{
		while(isJoining){
			try{
				Thread.sleep(100);
			}
			catch(Exception e){}
		}
		isJoining=true;
		String ip="";
        Byte[] ipAddr=null;
        int id=slaves.size();
		try{
			ip=getClientHost();
			ipAddr=getAddr(ip);
		}
		catch(Exception e){
			isJoining=false;
			throw new RemoteException(e.toString());
		}
		
		HangmanSlaveInfo si=new HangmanSlaveInfo();
		slaves.add(si);

		try{
            si.registry = LocateRegistry.getRegistry(ip.toString(),4244);
            si.server = (HangmanSlave) si.registry.lookup("hangmanSlave");
		}
		catch(Exception e){
			isJoining=false;
			throw new RemoteException(e.toString());
		}

		Runnable r=new HeartBeatSlave(this,si.server,ipAddr);
		new Thread(r).start();
		si.server.setLives(maxLives);
		try{
			distributeWords();
		}
		catch(Exception e){
			isJoining=false;
			Server.serverMsg("Falha ao distribuir palavras");
			System.exit(0);
		}
		slavesId.put(ipAddr,id);
		Server.serverMsg(ip.toString()+" se conectou como escravo");
		isJoining=false;
	}
	public void exit() throws RemoteException{
		String ip="";
        Byte[] ipAddr=null;
		try{
			ip=getClientHost();
			ipAddr=getAddr(ip);
		}
		catch(Exception e){throw new RemoteException(e.toString());}
        kickSlave(ipAddr);
	}
	public int beat() throws RemoteException{
		return 1;
	}
	//
}