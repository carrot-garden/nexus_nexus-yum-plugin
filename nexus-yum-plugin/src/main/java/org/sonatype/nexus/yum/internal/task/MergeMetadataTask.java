/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.yum.internal.task;

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.sonatype.nexus.yum.YumRepository.PATH_OF_REPOMD_XML;
import static org.sonatype.scheduling.TaskState.RUNNING;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.repository.GroupRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.scheduling.AbstractNexusTask;
import org.sonatype.nexus.scheduling.NexusScheduler;
import org.sonatype.nexus.yum.YumRepository;
import org.sonatype.nexus.yum.internal.RepoMD;
import org.sonatype.nexus.yum.internal.RepositoryUtils;
import org.sonatype.nexus.yum.internal.YumRepositoryImpl;
import org.sonatype.scheduling.ScheduledTask;
import org.sonatype.sisu.goodies.eventbus.EventBus;
import com.google.common.io.Closeables;

@Named( MergeMetadataTask.ID )
public class MergeMetadataTask
    extends AbstractNexusTask<YumRepository>
{

    private static final Logger LOG = LoggerFactory.getLogger( MergeMetadataTask.class );

    public static final String ID = "MergeMetadataTask";

    private static final int MAXIMAL_PARALLEL_RUNS = 1;

    private GroupRepository groupRepository;

    @Inject
    public MergeMetadataTask( final EventBus eventBus )
    {
        super( eventBus, null );
    }

    public void setGroupRepository( final GroupRepository groupRepository )
    {
        this.groupRepository = groupRepository;
    }

    @Override
    protected YumRepository doRun()
        throws Exception
    {
        if ( isValidRepository() )
        {
            deleteYumTempDirs();

            final File repoBaseDir = RepositoryUtils.getBaseDir( groupRepository );
            final List<File> memberReposBaseDirs = getBaseDirsOfMemberRepositories();
            if ( memberReposBaseDirs.size() > 1 )
            {
                LOG.debug( "Merging repository group '{}' out of {}", groupRepository.getId(), memberReposBaseDirs );
                new CommandLineExecutor().exec( buildCommand( repoBaseDir, memberReposBaseDirs ) );
                LOG.debug( "Group repository '{}' merged", groupRepository.getId() );
            }
            else
            {
                final File groupRepoData = new File( repoBaseDir, "repodata" );
                LOG.debug(
                    "Remove group repository repodata, because at maximum one yum member-repository left : {}",
                    groupRepoData
                );
                // TODO this should be done via repo API
                deleteQuietly( groupRepoData );
            }

            deleteYumTempDirs();

            return new YumRepositoryImpl( repoBaseDir, groupRepository.getId(), null );
        }
        return null;
    }

    private List<File> getBaseDirsOfMemberRepositories()
        throws URISyntaxException, MalformedURLException
    {
        final List<File> baseDirs = new ArrayList<File>();
        for ( final Repository memberRepository : groupRepository.getMemberRepositories() )
        {
            try
            {
                final StorageItem repomdItem = memberRepository.retrieveItem(
                    new ResourceStoreRequest( "/" + PATH_OF_REPOMD_XML )
                );
                if ( repomdItem instanceof StorageFileItem )
                {
                    InputStream in = null;
                    try
                    {
                        final RepoMD repomd = new RepoMD( in = ( (StorageFileItem) repomdItem ).getInputStream() );
                        // do we need them all or we can skip the sqllite ?
                        for ( final String location : repomd.getLocations() )
                        {
                            memberRepository.retrieveItem(
                                new ResourceStoreRequest( "/" + location )
                            );
                        }
                    }
                    finally
                    {
                        Closeables.closeQuietly( in );
                    }
                }
                // all metadata files are available by now so lets use it
                baseDirs.add( RepositoryUtils.getBaseDir( memberRepository ) );
            }
            catch ( Exception ignore )
            {
                // we do not have all the necessary files in member repository to get it merged
            }
        }
        return baseDirs;
    }

    private void deleteYumTempDirs()
        throws IOException
    {
        final String yumTmpDirPrefix = "yum-" + System.getProperty( "user.name" );
        final File tmpDir = new File( "/var/tmp" );
        if ( tmpDir.exists() )
        {
            final File[] yumTmpDirs = tmpDir.listFiles( new FilenameFilter()
            {

                @Override
                public boolean accept( File dir, String name )
                {
                    return name.startsWith( yumTmpDirPrefix );
                }
            } );
            for ( File yumTmpDir : yumTmpDirs )
            {
                LOG.debug( "Deleting yum temp dir : {}", yumTmpDir );
                deleteQuietly( yumTmpDir );
            }
        }
    }

    @Override
    public boolean allowConcurrentExecution( Map<String, List<ScheduledTask<?>>> activeTasks )
    {

        if ( activeTasks.containsKey( ID ) )
        {
            int activeRunningTasks = 0;
            for ( ScheduledTask<?> scheduledTask : activeTasks.get( ID ) )
            {
                if ( RUNNING.equals( scheduledTask.getTaskState() ) )
                {
                    if ( conflictsWith( (MergeMetadataTask) scheduledTask.getTask() ) )
                    {
                        return false;
                    }
                    activeRunningTasks++;
                }
            }
            return activeRunningTasks < MAXIMAL_PARALLEL_RUNS;
        }
        else
        {
            return true;
        }
    }

    private boolean conflictsWith( MergeMetadataTask task )
    {
        return task.getGroupRepository() != null && this.getGroupRepository() != null
            && task.getGroupRepository().getId().equals( getGroupRepository().getId() );
    }

    @Override
    protected String getAction()
    {
        return "GENERATE_YUM_GROUP_REPOSITORY";
    }

    @Override
    protected String getMessage()
    {
        return format( "Generate yum metadata for group repository %s='%s'", groupRepository.getId(),
                       groupRepository.getName() );
    }

    public GroupRepository getGroupRepository()
    {
        return groupRepository;
    }

    private boolean isValidRepository()
    {
        return groupRepository != null && !groupRepository.getMemberRepositories().isEmpty();
    }

    private String buildCommand( File repoBaseDir, List<File> memberRepoBaseDirs )
        throws MalformedURLException, URISyntaxException
    {
        final StringBuilder repos = new StringBuilder();
        for ( File memberRepoBaseDir : memberRepoBaseDirs )
        {
            repos.append( " --repo=" );
            repos.append( memberRepoBaseDir.toURI().toString() );
        }
        return format( "mergerepo --nogroups -d %s -o %s", repos.toString(), repoBaseDir.getAbsolutePath() );
    }

    public static ScheduledTask<YumRepository> createTaskFor( final NexusScheduler nexusScheduler,
                                                              final GroupRepository groupRepository )
    {
        final MergeMetadataTask task = nexusScheduler.createTaskInstance(
            MergeMetadataTask.class
        );
        task.setGroupRepository( groupRepository );
        return nexusScheduler.submit( MergeMetadataTask.ID, task );
    }

}
