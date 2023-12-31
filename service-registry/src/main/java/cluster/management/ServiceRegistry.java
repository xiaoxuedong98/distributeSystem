package cluster.management;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//using service registry, our cluster can scale without any modifications
public class ServiceRegistry implements Watcher {
  private static final String REGISTRY_ZNODE = "/service_registry";
  private final ZooKeeper zooKeeper;
  private String currentZnode = null;
  private List<String> allServiceAddresses = null;

  public ServiceRegistry(ZooKeeper zooKeeper) {
    this.zooKeeper = zooKeeper;
    createServiceRegistryZnode();
  }

  //register to cluster by publishing their address
  public void registerToCluster(String metadata) throws KeeperException, InterruptedException {
    if (this.currentZnode != null) {
      System.out.println("Already registered to service registry");
      return;
    }
    this.currentZnode = zooKeeper.create(REGISTRY_ZNODE + "/n_", metadata.getBytes(),
        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
    System.out.println("Registered to service registry");
  }

  //register for updates to get any node's address
  public void registerForUpdates() {
    try {
      updateAddresses();
    } catch (KeeperException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void createServiceRegistryZnode() {
    try {
      if (zooKeeper.exists(REGISTRY_ZNODE, false) == null) {
        //it creates it as a persistent znode with an empty data array
        //and sets the ACL (Access Control List) to ZooDefs.Ids.OPEN_ACL_UNSAFE.
        zooKeeper.create(REGISTRY_ZNODE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }
    } catch (KeeperException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public synchronized List<String> getAllServiceAddresses() throws KeeperException, InterruptedException {
    if (allServiceAddresses == null) {
      updateAddresses();
    }
    return allServiceAddresses;
  }

  public void unregisterFromCluster() {
    try {
      if (currentZnode != null && zooKeeper.exists(currentZnode, false) != null) {
        zooKeeper.delete(currentZnode, -1);
      }
    } catch (KeeperException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private synchronized void updateAddresses() throws KeeperException, InterruptedException {
    List<String> workerZnodes = zooKeeper.getChildren(REGISTRY_ZNODE, this);

    List<String> addresses = new ArrayList<>(workerZnodes.size());

    for (String workerZnode : workerZnodes) {
      //find existing worker node
      String workerFullPath = REGISTRY_ZNODE + "/" + workerZnode;
      Stat stat = zooKeeper.exists(workerFullPath, false);
      if (stat == null) {
        continue;
      }
      //if znode exists, get the data stored in the znode, convert the byte data to string
      byte[] addressBytes = zooKeeper.getData(workerFullPath, false, stat);
      String address = new String(addressBytes);
      addresses.add(address);
    }

    //unmodifiableList prevent modification
    this.allServiceAddresses = Collections.unmodifiableList(addresses);
    System.out.println("The cluster addresses are: " + this.allServiceAddresses);
  }

  @Override
  public void process(WatchedEvent event) {
    try {
      updateAddresses();
    } catch (KeeperException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}

