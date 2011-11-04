package de.is24.nexus.yum;

import static de.is24.nexus.yum.repository.utils.RepositoryTestUtils.BASE_TMP_FILE;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import java.io.File;
import javax.inject.Inject;
import com.google.code.tempusfugit.temporal.Condition;
import com.google.code.tempusfugit.temporal.Duration;
import com.google.code.tempusfugit.temporal.ThreadSleep;
import com.google.code.tempusfugit.temporal.Timeout;
import com.google.code.tempusfugit.temporal.WaitFor;
import de.is24.nexus.yum.guice.NexusTestRunner;
import de.is24.nexus.yum.repository.utils.RepositoryTestUtils;
import de.is24.nexus.yum.service.impl.DefaultYumService;
import de.is24.nexus.yum.service.impl.YumRepositoryCreatorService;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.proxy.RequestContext;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.maven.MavenHostedRepository;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;


@RunWith(NexusTestRunner.class)
public abstract class AbstractRepositoryTester {
  private static final String SNAPSHOTS = "snapshots";
  private static final Logger log = LoggerFactory.getLogger(AbstractRepositoryTester.class);

  private static final long SLEEP_TIME = 30;
  private static final long MAX_TIMEOUT = 10000;

  @Inject
  private DefaultYumService yumService;

  @Inject
  private YumRepositoryCreatorService yumRepositoryCreatorService;

  @After
  public void waitForThreadPool() throws Exception {
    WaitFor.waitOrTimeout(new Condition() {
        public boolean isSatisfied() {
          return yumRepositoryCreatorService.getActiveWorkerCount() == 0;
        }
      }, Timeout.timeout(Duration.seconds(60)), new ThreadSleep(Duration.millis(30)));
  }


  @Before
  public void cleanUpCacheDirectory() throws Exception {
    deleteDirectory(BASE_TMP_FILE);
  }

  protected MavenRepository createRepository(final boolean isMavenHostedRepository) {
    return createRepository(isMavenHostedRepository, SNAPSHOTS);
  }

  protected MavenRepository createRepository(final boolean isMavenHostedRepository, final String repoId) {
    RepositoryKind kind = createMock(RepositoryKind.class);
    expect(kind.isFacetAvailable(MavenHostedRepository.class)).andReturn(isMavenHostedRepository);

    MavenRepository repository = createMock(MavenRepository.class);
    expect(repository.getRepositoryKind()).andReturn(kind).anyTimes();
    expect(repository.getId()).andReturn(repoId).anyTimes();

    File repoDir = new File(RepositoryTestUtils.BASE_TMP_FILE, "tmp-repos/" + repoId);
    repoDir.mkdirs();
    expect(repository.getLocalUrl()).andReturn(repoDir.toURI().toString()).anyTimes();

    replay(kind, repository);

    return repository;
  }

  protected StorageItem createItem(String version, String filename) {
    StorageItem item = createMock(StorageItem.class);

    expect(item.getPath()).andReturn("blalu/" + version + "/" + filename).anyTimes();
    expect(item.getParentPath()).andReturn("blalu/" + version).anyTimes();
    expect(item.getItemContext()).andReturn(new RequestContext()).anyTimes();

    replay(item);
    return item;
  }
}
