import cluster.management.LeaderElection;
import cluster.management.ServiceRegistry;
import java.io.IOException;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class Application implements Watcher {
  private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
  private static final int SESSION_TIMEOUT = 3000;
  private static final int DEFAULT_PORT = 8080;
  private ZooKeeper zooKeeper;

  public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
    //if we run the application instances on different computers, can simply use default port, as the address of
    //each node will be different
    int currentServerPort = args.length == 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
    Application application = new Application();
    ZooKeeper zooKeeper = application.connectToZookeeper();

    ServiceRegistry serviceRegistry = new ServiceRegistry(zooKeeper);

    OnElectionAction onElectionAction = new OnElectionAction(serviceRegistry, currentServerPort);

    LeaderElection leaderElection = new LeaderElection(zooKeeper, onElectionAction);
    leaderElection.volunteerForLeadership();
    leaderElection.reelectLeader();

    application.run();
    application.close();
    System.out.println("Disconnected from Zookeeper, exiting application");
  }

  public ZooKeeper connectToZookeeper() throws IOException {
    this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    return zooKeeper;
  }

  public void run() throws InterruptedException {
    synchronized (zooKeeper) {
      zooKeeper.wait();
    }
  }

  public void close() throws InterruptedException {
    zooKeeper.close();
  }

  @Override
  public void process(WatchedEvent event) {
    switch (event.getType()) {
      case None:
        if (event.getState() == Event.KeeperState.SyncConnected) {
          System.out.println("Successfully connected to Zookeeper");
        } else {
          synchronized (zooKeeper) {
            System.out.println("Disconnected from Zookeeper event");
            zooKeeper.notifyAll();
          }
        }
    }
  }
}
