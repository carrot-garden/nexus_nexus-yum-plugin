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
package org.sonatype.nexus.yum.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.configuration.application.NexusConfiguration;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.StringContentLocator;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.scheduling.NexusScheduler;
import org.sonatype.nexus.yum.Yum;
import org.sonatype.nexus.yum.YumRegistry;
import org.sonatype.nexus.yum.internal.task.RepositoryScanningTask;

@Named
@Singleton
public class YumRegistryImpl
    implements YumRegistry
{

    private static final Logger LOG = LoggerFactory.getLogger( YumRegistryImpl.class );

    private final Map<String, Yum> yums = new ConcurrentHashMap<String, Yum>();

    private final NexusConfiguration nexusConfiguration;

    private final NexusScheduler nexusScheduler;

    private final YumFactory yumFactory;

    private int maxNumberOfParallelThreads;

    @Inject
    public YumRegistryImpl( final NexusConfiguration nexusConfiguration,
                            final NexusScheduler nexusScheduler,
                            final YumFactory yumFactory )
    {
        this.nexusConfiguration = checkNotNull( nexusConfiguration );
        this.nexusScheduler = checkNotNull( nexusScheduler );
        this.yumFactory = checkNotNull( yumFactory );
        this.maxNumberOfParallelThreads = DEFAULT_MAX_NUMBER_PARALLEL_THREADS;
    }

    @Override
    public Yum register( final MavenRepository repository )
    {
        if ( !yums.containsKey( repository.getId() ) )
        {
            final Yum yum = yumFactory.create( getTemporaryDirectory(), repository );
            yums.put( repository.getId(), yum );

            LOG.info( "Registered repository '{}' as Yum repository", repository.getId() );

            createVirtualYumConfigFile( repository );

            if ( repository.getRepositoryKind().isFacetAvailable( HostedRepository.class ) )
            {
                runScanningTask( yum );
            }

            return yum;
        }
        return yums.get( repository.getId() );
    }

    @Override
    public Yum unregister( final String repositoryId )
    {
        final Yum yum = yums.remove( repositoryId );
        if ( yum != null )
        {
            LOG.info( "Unregistered repository '{}' as Yum repository", repositoryId );
        }
        return yum;

    }

    @Override
    public Yum get( final String repositoryId )
    {
        return yums.get( repositoryId );
    }

    private void runScanningTask( final Yum yum )
    {
        RepositoryScanningTask task = nexusScheduler.createTaskInstance( RepositoryScanningTask.class );
        task.setYum( yum );
        nexusScheduler.submit( RepositoryScanningTask.ID, task );
    }

    @Override
    public boolean isRegistered( String repositoryId )
    {
        return yums.containsKey( repositoryId );
    }

    @Override
    public YumRegistry setMaxNumberOfParallelThreads( final int maxNumberOfParallelThreads )
    {
        this.maxNumberOfParallelThreads = maxNumberOfParallelThreads;

        return this;
    }

    @Override
    public int maxNumberOfParallelThreads()
    {
        return maxNumberOfParallelThreads;
    }

    @Override
    public File getTemporaryDirectory()
    {
        return new File( nexusConfiguration.getTemporaryDirectory(), "nexus-yum-plugin" );
    }

    private void createVirtualYumConfigFile( final MavenRepository repository )
    {
        DefaultStorageFileItem file = new DefaultStorageFileItem(
            repository,
            new ResourceStoreRequest( YumConfigContentGenerator.configFilePath( repository.getId() ) ),
            true,
            false,
            new StringContentLocator( YumConfigContentGenerator.ID )
        );
        file.setContentGeneratorId( YumConfigContentGenerator.ID );

        try
        {
            repository.storeItem( false, file );
        }
        catch ( Exception e )
        {
            LOG.warn( "Could not store '{}'", file, e );
        }
    }

}
