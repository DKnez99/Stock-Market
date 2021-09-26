package application;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class RequestServant extends Thread{
	private Socket socket;
	private static LinkedBlockingQueue<Vector<String>> requests=new LinkedBlockingQueue<Vector<String>>();
	private static LinkedBlockingQueue<Vector<String>> clients=new LinkedBlockingQueue<Vector<String>>();
	private static LinkedBlockingQueue<Vector<String>> subservers=new LinkedBlockingQueue<Vector<String>>();
	private static LinkedBlockingQueue<Vector<String>> stocks=new LinkedBlockingQueue<Vector<String>>();
	private static double clientRR;
	private static double subserverRR;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;
	
	static Semaphore semaphoreSQ=new Semaphore(1);
	static Semaphore semaphoreSSTA=new Semaphore(1);
	static Semaphore semaphoreSLTA=new Semaphore(1);
	static Semaphore semaphoreCTA=new Semaphore(1);
	static Semaphore semaphoreSTA=new Semaphore(1);
	
	private static DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		
	@FXML
	private static TextArea SubserversTextArea;
	
	@FXML
	private static TextArea ServerLogsTextArea;
	
	@FXML
	private static TextArea ClientsTextArea;
	
	@FXML
	private static TextArea StocksTextArea;
	
	public RequestServant(double cRR, double sRR, Socket s, LinkedBlockingQueue<Vector<String>> req, LinkedBlockingQueue<Vector<String>> cli, LinkedBlockingQueue<Vector<String>> sub, LinkedBlockingQueue<Vector<String>> stk, TextArea ssta, TextArea slta, TextArea cta, TextArea sta) {
		clientRR=cRR;
		subserverRR=sRR;
		this.socket=s;
		if(!(requests.size()>0))
			requests=req; //CHECK
		if(!(clients.size()>0))
			clients=cli; //CHECK
		if(!(subservers.size()>0))
			subservers=sub; //CHECK
		if(!(stocks.size()>0))
			stocks=stk; //CHECK
		SubserversTextArea=ssta;
		ServerLogsTextArea=slta;
		ClientsTextArea=cta;
		StocksTextArea=sta;
	}
	
	@Override
	public void run() {
        try {
        	oos = new ObjectOutputStream(socket.getOutputStream());
        	ois = new ObjectInputStream(socket.getInputStream());

        	Vector<String> temp = (Vector<String>)ois.readObject();	
        	String operation=temp.remove(0);				//first string is always name of the operation
        	
        	semaphoreSLTA.acquireUninterruptibly();
    		ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Accepted '"+operation+"' request from "
    				+socket.getInetAddress().toString().substring(1, socket.getInetAddress().toString().length())
    				+"/"+socket.getPort()+"\n\n");
    		semaphoreSLTA.release();
        			
        	switch(operation) {
        	case "client connect":{
        			if(RequestServant.clients.offer(temp)) {	//if client[username, password, ???clientServerSocket???] can be added to lbq clients
        				Platform.runLater(()->{
        					semaphoreCTA.acquireUninterruptibly();
        					ClientsTextArea.appendText(temp.elementAt(0)+"\n");
        					semaphoreCTA.release();
        				});
        				String succ="successful";
        				oos.writeObject(succ);
        				oos.flush();
        				
        				Platform.runLater(()->{//remove later
        					semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'successful' connect to "
        							+socket.getInetAddress().toString().substring(1, socket.getInetAddress().toString().length())
        							+"/"+socket.getPort()+"\n\n");
        					semaphoreSLTA.release();
        				});
        				while(RequestServant.clients.contains(temp)) {
        					sleep((int)clientRR*1000);
        					//StocksTextArea.clear();
        					oos.writeObject("begin");
        					oos.flush();
        					stocks.forEach(row->{
        						//StocksTextArea.appendText(row.elementAt(0)+"\t"+row.elementAt(1)+"\t"+row.elementAt(2)+"\t"+row.elementAt(3)+"\n");
        						try {
        							String sender=row.elementAt(0)+"\t\t"+row.elementAt(1)+"\t\t\t"+row.elementAt(2)+"\t\t"+row.elementAt(3)+"\n";
									oos.writeObject(sender);
									oos.flush();
        						} catch (Exception e) {
        							semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Can't send stocks to "
                							+socket.getInetAddress().toString().substring(1, socket.getInetAddress().toString().length())
                							+"/"+socket.getPort()+"\n\n");
                					semaphoreSLTA.release();
								}        					
    						});
        					//oos.writeObject("end");
        					//oos.flush();
        				}
        			}
        			else {
        				oos.writeObject("not successful");
        				oos.flush();
    				}
        			oos.close();
    				ois.close();
    				socket.close();
        			break;
        		}
        	case "client disconnect":{
				if(RequestServant.clients.remove(temp)) {
					Platform.runLater(()->{
						semaphoreCTA.acquireUninterruptibly();
						ClientsTextArea.clear();
						clients.forEach(row->{
							ClientsTextArea.appendText(row.elementAt(0)+"\n");
						});
						semaphoreCTA.release();
					});
					
    				oos.writeObject("successful");
    				oos.flush();
    				
    				Platform.runLater(()->{//remove later
    				semaphoreSLTA.acquireUninterruptibly();
    				ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'successful disconnect' to "
    						+socket.getInetAddress().toString().substring(1, socket.getInetAddress().toString().length())
    						+"/"+socket.getPort()+"\n\n");
    				semaphoreSLTA.release();
    				});
    			}
				else {
					oos.writeObject("not successful");
    				oos.flush();
    				Platform.runLater(()->{
    					semaphoreSLTA.acquireUninterruptibly();
    					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'cant disconnect' to "
    							+socket.getInetAddress().toString().substring(1, socket.getInetAddress().toString().length())
    							+"/"+socket.getPort()+"\n\n");
    					semaphoreSLTA.release();
    				});
				}
				oos.close();
				ois.close();
				socket.close();
    			break;
        		
        	}
        		case "subserver connect":{
        			if(!RequestServant.subservers.contains(temp) && RequestServant.subservers.offer(temp)) {	//if subserver[ipa, sp] isnt already online and can be added to lbq subservers
        				
        				oos.writeObject("successful");
        				oos.flush();
        				
        				Platform.runLater(()->{//remove later
        					semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'successful' connect to "
        							+socket.getInetAddress().toString().substring(1)
        							+"/"+socket.getPort()+"\n\n");
        					semaphoreSLTA.release();
        				});
        				
        				   				
        				AtomicBoolean done=new AtomicBoolean(false);
        				while(!done.get()) {
        					done.set(true);
        					//LOAD BALANCING
        					int numberOfStocks=RequestServant.stocks.size();
        					int numberOfSubservers=RequestServant.subservers.size();
        				
        					LinkedBlockingQueue<Vector<String>> helperQueue=new LinkedBlockingQueue<Vector<String>>();
        					Vector<String> subserverIPAs=new Vector<String>();
        					RequestServant.subservers.forEach(row->{
        						subserverIPAs.add(row.elementAt(0));
        					});
        				
        					int cnt=0;
        					
        					semaphoreSQ.acquireUninterruptibly();
        					Iterator<Vector<String>> stocksIterator=RequestServant.stocks.iterator();
        				
        					Vector<String> helperVector=new Vector<String>();
        				
        					while(numberOfStocks>0) {	//change subserver in charge of every stock
        						helperVector=stocksIterator.next();
        						helperVector.set(4,subserverIPAs.elementAt(cnt));
        						cnt=(cnt+1)%numberOfSubservers;
        						numberOfStocks--;
        						helperQueue.offer(helperVector);
        					}
        				
        					RequestServant.stocks=helperQueue;
        					semaphoreSQ.release();

        					Platform.runLater(()->{
        						semaphoreSSTA.acquireUninterruptibly();
        						SubserversTextArea.clear();     					
        						RequestServant.subservers.forEach(subserver->{
        							SubserversTextArea.appendText(subserver.elementAt(0)+"/"+subserver.elementAt(1)+"\n");
        							RequestServant.stocks.forEach(stock->{
            							if(subserver.elementAt(0)==stock.elementAt(4))
            								SubserversTextArea.appendText(stock.elementAt(0)+"\n");
            						});
        							SubserversTextArea.appendText("\n");
        						});
        						semaphoreSSTA.release();
        					});
        				
        					RequestServant.subservers.forEach(subserver->{//receive buy and sell requests from all subservers     						
        						try {
        							Socket socketSubserver=new Socket(InetAddress.getByName(subserver.elementAt(0)), Integer.parseInt(subserver.elementAt(1)));
    								ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
    								ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
        						
        							oosSubserver.writeObject("get requests");
        							oosSubserver.flush();
        							
        							Platform.runLater(()->{//remove later
        	        					semaphoreSLTA.acquireUninterruptibly();
        	        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'get requests' connect to "
        	        							+subserver.elementAt(0)
        	        							+"/"+subserver.elementAt(1)+"\n\n");
        	        					semaphoreSLTA.release();
        	        				});
        							
        							Vector<String> receiver = (Vector<String>)oisSubserver.readObject();	
        							Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
        							
        							while(!receiver.elementAt(0).equalsIgnoreCase("end")) {
        								RequestServant.requests.offer(receiver);
        								receiver = (Vector<String>)oisSubserver.readObject();
        								Platform.runLater(()->{//remove later
        		        					semaphoreSLTA.acquireUninterruptibly();
        		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
        		        							+subserver.elementAt(0)
        		        							+"/"+subserver.elementAt(1)+"\n\n");
        		        					semaphoreSLTA.release();
        		        				});
        							}
        							//maybe we dont have to close them
        							oisSubserver.close();
        							oosSubserver.close();
        							socketSubserver.close();
        							
        						} catch (Exception ex) {
        							semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot get buy and sell requests from "
                							+subserver.elementAt(0)
                							+"/"+subserver.elementAt(1)+"\n\n");
                					semaphoreSLTA.release();
        							done.set(false);
        						}
        					});
        				
        					
        					//send stocks and queues to subservers
        					RequestServant.subservers.forEach(subserver->{
        						try {
        							Socket socketSubserver=new Socket(InetAddress.getByName(subserver.elementAt(0)), Integer.parseInt(subserver.elementAt(1)));
    								ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
    								ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
    								oosSubserver.writeObject("insert data");
    								oosSubserver.flush();
    								Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Requested 'insert data' from "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
    								
    								semaphoreSQ.acquireUninterruptibly();
        							RequestServant.stocks.forEach(stock->{	//send stock values
            							if(subserver.elementAt(0)==stock.elementAt(4)) {
            								try {
            									oosSubserver.writeObject("stock");
            									oosSubserver.flush();
												oosSubserver.writeObject(stock);
												oosSubserver.flush();
												Platform.runLater(()->{//remove later
			    		        					semaphoreSLTA.acquireUninterruptibly();
			    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent stock "
			    		        							+stock+" to "
			    		        							+subserver.elementAt(0)
			    		        							+"/"+subserver.elementAt(1)+"\n\n");
			    		        					semaphoreSLTA.release();
			    		        				});
												RequestServant.requests.forEach(request->{	//send requests for that stock
													if(stock.elementAt(0)==request.elementAt(3)) {
														try {
															oosSubserver.writeObject("request");
															oosSubserver.flush();
															oosSubserver.writeObject(request);
															oosSubserver.flush();
															Platform.runLater(()->{//remove later
						    		        					semaphoreSLTA.acquireUninterruptibly();
						    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent request "
						    		        							+request+" to "
						    		        							+subserver.elementAt(0)
						    		        							+"/"+subserver.elementAt(1)+"\n\n");
						    		        					semaphoreSLTA.release();
						    		        				});
														} catch (IOException e) {
															semaphoreSLTA.acquireUninterruptibly();
						                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send buy/sell requests to "
						                							+subserver.elementAt(0)
						                							+"/"+subserver.elementAt(1)+"\n"+
						                							"Error: "+e.toString()+"\n\n");
						                					semaphoreSLTA.release();
						        							done.set(false);
														}	
													}
												});											
											} catch (IOException e) {
												semaphoreSLTA.acquireUninterruptibly();
			                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send stock to "
			                							+subserver.elementAt(0)
			                							+"/"+subserver.elementAt(1)+"\n"+
			                							"Error: "+e.toString()+"\n\n");
			                					semaphoreSLTA.release();
			        							done.set(false);
											}
            							}
            						});
        							semaphoreSQ.release();
        							oosSubserver.writeObject("end");
        							oosSubserver.flush();
        							
        							Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'end' to "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
        							//check if all subservers received everything, use some sort of counter
        							//String success=(String)oisSubserver.readObject();
        							//if(success.equalsIgnoreCase("success"))
        							//	done.set(true);
        						}	catch (Exception ex){
        							semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send current stocks and requests to "
                							+subserver.elementAt(0)
                							+"/"+subserver.elementAt(1)+"\n\n");
        							done.set(false);
        						}   							
    						});
        					RequestServant.requests.clear();	//delete everything from request queue
        					
        					//now after load balancing we can request stocks data from subserver every Y seconds
        					while(RequestServant.subservers.contains(temp)) {
            					sleep((int)subserverRR*1000);
            					//StocksTextArea.clear();
            					
            					Socket socketSubserver=new Socket(InetAddress.getByName(temp.elementAt(0)), Integer.parseInt(temp.elementAt(1)));
								ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
								ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
            					
								oosSubserver.writeObject("get stocks");
								oosSubserver.flush();
								
            					helperQueue=new LinkedBlockingQueue<Vector<String>>();
            					semaphoreSQ.acquireUninterruptibly();
            					stocksIterator=RequestServant.stocks.iterator();		
            					helperVector=new Vector<String>();           				
            					int counter=RequestServant.stocks.size();  					          					
            					Vector<String> receiver=(Vector<String>)oisSubserver.readObject();
            					
            					/*Platform.runLater(()->{//remove later
                					semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Receiving stocks from "
                							+temp.elementAt(0)
                							+"/"+temp.elementAt(1)+"\n\n");
                					semaphoreSLTA.release();
                				});*/
            					
            					while(counter>0) {	//lepse napisati kod
            						helperVector=stocksIterator.next();
            						if(!receiver.elementAt(0).equalsIgnoreCase("end") ) {
            							if(helperVector.elementAt(0).equalsIgnoreCase(receiver.elementAt(0))) {
            								helperVector=receiver;
            								
            								
            								
            								final Vector<String> hv=helperVector;
            								/*Platform.runLater(()->{//remove later
                            					semaphoreSLTA.acquireUninterruptibly();
                            					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Received stock "+hv+" from "
                            							+temp.elementAt(0)
                            							+"/"+temp.elementAt(1)+"\n\n");
                            					semaphoreSLTA.release();
                            				});*/
            								
            								receiver=(Vector<String>)oisSubserver.readObject();
            							}
            							
            						}
            						helperQueue.offer(helperVector);
            					    counter--;
            					}
            					
            					RequestServant.stocks=helperQueue;
            					
            					//we need separate thread to print out all stocks at once
            					
            					semaphoreSTA.acquireUninterruptibly();
            					StocksTextArea.clear();
            					RequestServant.stocks.forEach(stock->{
            						StocksTextArea.appendText(stock.elementAt(0)+"\t\t"+stock.elementAt(1)+"\t\t\t"+stock.elementAt(2)+"\t\t"+stock.elementAt(3)+"\n");
            					});
            					semaphoreSTA.release();
            					
            					semaphoreSQ.release();
            				}
        					//maybe close sockets
        				}
        			}
        			else {
        				oos.writeObject("not successful");
        				oos.flush();
        				Platform.runLater(()->{//remove later
        					semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'not successful' to "
        							+socket.getInetAddress().toString().substring(1)
        							+"/"+socket.getPort()+"\n\n");
        					semaphoreSLTA.release();
        				});
    				}
        			oos.close();
    				ois.close();
    				socket.close();
        			break;
        		//}
        		}
        		case "subserver disconnect":{
        			if(RequestServant.subservers.contains(temp)) {	//get pending buy/sell requests from subserver which is going to disconnect
        				try {
							Socket socketSubserver=new Socket(InetAddress.getByName(temp.elementAt(0)), Integer.parseInt(temp.elementAt(1)));
							ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
							ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
						
							oosSubserver.writeObject("get requests");
							oosSubserver.flush();
							
							Platform.runLater(()->{//remove later
	        					semaphoreSLTA.acquireUninterruptibly();
	        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'get requests' to "
	        							+temp.elementAt(0)
	        							+"/"+temp.elementAt(1)+"\n\n");
	        					semaphoreSLTA.release();
	        				});
							
							Vector<String> receiver = (Vector<String>)oisSubserver.readObject();	
							Platform.runLater(()->{//remove later
	        					semaphoreSLTA.acquireUninterruptibly();
	        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
	        							+temp.elementAt(0)
	        							+"/"+temp.elementAt(1)+"\n\n");
	        					semaphoreSLTA.release();
	        				});
							
							while(!receiver.elementAt(0).equalsIgnoreCase("end")) {
								RequestServant.requests.offer(receiver);
								receiver = (Vector<String>)oisSubserver.readObject();
								Platform.runLater(()->{//remove later
		        					semaphoreSLTA.acquireUninterruptibly();
		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
		        							+temp.elementAt(0)
		        							+"/"+temp.elementAt(1)+"\n\n");
		        					semaphoreSLTA.release();
		        				});
							}
							//maybe we dont have to close them
							oisSubserver.close();
							oosSubserver.close();
							socketSubserver.close();
							
						} catch (Exception ex) {
							semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot get buy and sell requests from "
        							+temp.elementAt(0)
        							+"/"+temp.elementAt(1)+"\n\n");
        					semaphoreSLTA.release();
						}
        			}
        			
        			if(RequestServant.subservers.remove(temp)) {	//if subserver[ipa, sp] is among connected subservers			
        				   				
        				AtomicBoolean done=new AtomicBoolean(false);	//LOAD BALANCING
        				while(!done.get()) {
        					done.set(true);
        					
        					int numberOfStocks=RequestServant.stocks.size();
        					int numberOfSubservers=RequestServant.subservers.size();
        				
        					LinkedBlockingQueue<Vector<String>> helperQueue=new LinkedBlockingQueue<Vector<String>>();
        					Vector<String> subserverIPAs=new Vector<String>();
        					if(numberOfSubservers>0) {
        						RequestServant.subservers.forEach(subserver->{
        							subserverIPAs.add(subserver.elementAt(0));
        						});
        					}
        					
        					int cnt=0;
        					Iterator<Vector<String>> stocksIterator=RequestServant.stocks.iterator();
        				
        					Vector<String> helperVector=new Vector<String>();
        				
        					while(numberOfStocks>0) {	//change subserver in charge of every stock
        						helperVector=stocksIterator.next();
        						if(numberOfSubservers>0) {
        							helperVector.set(4,subserverIPAs.elementAt(cnt));
        							cnt=(cnt+1)%numberOfSubservers;
        						}
        						else {
        							helperVector.set(4,"empty");
        						}
        						numberOfStocks--;
        						helperQueue.offer(helperVector);
        					}
        				
        					RequestServant.stocks=helperQueue;
        					
        					Platform.runLater(()->{
        						semaphoreSSTA.acquireUninterruptibly();
        						SubserversTextArea.clear();     					
        						RequestServant.subservers.forEach(subserver->{
        							SubserversTextArea.appendText(subserver.elementAt(0)+"/"+subserver.elementAt(1)+"\n");
        							RequestServant.stocks.forEach(stock->{
            							if(subserver.elementAt(0)==stock.elementAt(4))
            								SubserversTextArea.appendText(stock.elementAt(0)+"\n");
            						});
        							SubserversTextArea.appendText("\n");
        						});
        						semaphoreSSTA.release();
        					});
        				
        					RequestServant.subservers.forEach(subserver->{//receive buy and sell requests from all subservers     						
        						try {
        							Socket socketSubserver=new Socket(InetAddress.getByName(subserver.elementAt(0)), Integer.parseInt(subserver.elementAt(1)));
    								ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
    								ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
        						
        							oosSubserver.writeObject("get requests");
        							oosSubserver.flush();
        							
        							Platform.runLater(()->{//remove later
        	        					semaphoreSLTA.acquireUninterruptibly();
        	        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'get requests' to "
        	        							+subserver.elementAt(0)
        	        							+"/"+subserver.elementAt(1)+"\n\n");
        	        					semaphoreSLTA.release();
        	        				});
        							
        							Vector<String> receiver = (Vector<String>)oisSubserver.readObject();	
        							Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
        							
        							while(!receiver.elementAt(0).equalsIgnoreCase("end")) {
        								RequestServant.requests.offer(receiver);
        								receiver = (Vector<String>)oisSubserver.readObject();
        								Platform.runLater(()->{//remove later
        		        					semaphoreSLTA.acquireUninterruptibly();
        		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Read request from "
        		        							+subserver.elementAt(0)
        		        							+"/"+subserver.elementAt(1)+"\n\n");
        		        					semaphoreSLTA.release();
        		        				});
        							}
        							//maybe we dont have to close them
        							oisSubserver.close();
        							oosSubserver.close();
        							socketSubserver.close();
        							
        						} catch (Exception ex) {
        							semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot get buy and sell requests from "
                							+subserver.elementAt(0)
                							+"/"+subserver.elementAt(1)+"\n\n");
                					semaphoreSLTA.release();
        							done.set(false);
        						}
        					});
        						
        					//send stocks and queues to subservers (empty requests queue after this)
        					RequestServant.subservers.forEach(subserver->{
        						try {
        							Socket socketSubserver=new Socket(InetAddress.getByName(subserver.elementAt(0)), Integer.parseInt(subserver.elementAt(1)));
    								ObjectOutputStream oosSubserver = new ObjectOutputStream(socketSubserver.getOutputStream());
    								ObjectInputStream oisSubserver = new ObjectInputStream(socketSubserver.getInputStream());
    								oosSubserver.writeObject("insert data");
    								oosSubserver.flush();
    								Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Requested 'insert data' from "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
        							RequestServant.stocks.forEach(stock->{	//send stock values
            							if(subserver.elementAt(0)==stock.elementAt(4)) {
            								try {
            									oosSubserver.writeObject("stock");
            									oosSubserver.flush();
												oosSubserver.writeObject(stock);
												oosSubserver.flush();
												Platform.runLater(()->{//remove later
			    		        					semaphoreSLTA.acquireUninterruptibly();
			    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent stock "
			    		        							+stock+" to "
			    		        							+subserver.elementAt(0)
			    		        							+"/"+subserver.elementAt(1)+"\n\n");
			    		        					semaphoreSLTA.release();
			    		        				});
												RequestServant.requests.forEach(request->{	//send requests for that stock
													if(stock.elementAt(0)==request.elementAt(3)) {
														try {
															oosSubserver.writeObject("request");
															oosSubserver.flush();
															oosSubserver.writeObject(request);
															oosSubserver.flush();
															Platform.runLater(()->{//remove later
						    		        					semaphoreSLTA.acquireUninterruptibly();
						    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent request "
						    		        							+request+" to "
						    		        							+subserver.elementAt(0)
						    		        							+"/"+subserver.elementAt(1)+"\n\n");
						    		        					semaphoreSLTA.release();
						    		        				});
														} catch (IOException e) {
															semaphoreSLTA.acquireUninterruptibly();
						                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send buy/sell requests to "
						                							+subserver.elementAt(0)
						                							+"/"+subserver.elementAt(1)+"\n"+
						                							"Error: "+e.toString()+"\n\n");
						                					semaphoreSLTA.release();
						        							done.set(false);
														}	
													}
												});											
											} catch (IOException e) {
												semaphoreSLTA.acquireUninterruptibly();
			                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send stock to "
			                							+subserver.elementAt(0)
			                							+"/"+subserver.elementAt(1)+"\n"+
			                							"Error: "+e.toString()+"\n\n");
			                					semaphoreSLTA.release();
			        							done.set(false);
											}
            							}
            						});
        							oosSubserver.writeObject("end");
        							oosSubserver.flush();
        							
        							Platform.runLater(()->{//remove later
    		        					semaphoreSLTA.acquireUninterruptibly();
    		        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'end' to "
    		        							+subserver.elementAt(0)
    		        							+"/"+subserver.elementAt(1)+"\n\n");
    		        					semaphoreSLTA.release();
    		        				});
        							//check if all subservers received everything, use some sort of counter
        							//String success=(String)oisSubserver.readObject();
        							//if(success.equalsIgnoreCase("success"))
        							//	done.set(true);
        						}	catch (Exception ex){
        							semaphoreSLTA.acquireUninterruptibly();
                					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Cannot send current stocks and requests to "
                							+subserver.elementAt(0)
                							+"/"+subserver.elementAt(1)+"\n\n");
        							done.set(false);
        						}   							
    						});
        					RequestServant.requests.clear();	//delete everything from request queue
        					//maybe close sockets
        				}
        				
        				oos.writeObject("successful");	//confirm that disconnect was successful
        				oos.flush();
        				Platform.runLater(()->{//remove later
        					semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'successful' connect to "
        							+socket.getInetAddress().toString().substring(1)
        							+"/"+socket.getPort()+"\n\n");
        					semaphoreSLTA.release();
        				});
        			}
        			else {
        				oos.writeObject("not successful");
        				oos.flush();
        				
        				Platform.runLater(()->{//remove later
        					semaphoreSLTA.acquireUninterruptibly();
        					ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Sent 'not successful' to "
        							+socket.getInetAddress().toString().substring(1)
        							+"/"+socket.getPort()+"\n\n");
        					semaphoreSLTA.release();
        				});
    				}
        			oos.close();
    				ois.close();
    				socket.close();	
        			break;
        		}
        	}
        	
		} catch (Exception ex) {
			Platform.runLater(()->{
				semaphoreSLTA.acquireUninterruptibly();
				ServerLogsTextArea.appendText("["+dateFormat.format(new Date())+"] Error! Can't establish communication with "
						+socket.getInetAddress().toString().substring(1)
						+"/"+socket.getPort()+"\nError: "+ex.toString()+"\n\n");
				semaphoreSLTA.release();
			});
		}
	}
}
