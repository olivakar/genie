package com.netflix.genie.web.tasks.job

import com.netflix.genie.common.dto.Job
import com.netflix.genie.common.dto.JobStatus
import com.netflix.genie.common.exceptions.GenieServerException
import com.netflix.genie.core.events.JobFinishedEvent
import com.netflix.genie.core.events.JobFinishedReason
import com.netflix.genie.core.services.JobPersistenceService
import com.netflix.genie.core.services.JobSearchService
import com.netflix.genie.core.services.MailService
import com.netflix.genie.core.services.impl.GenieFileTransferService
import com.netflix.genie.test.categories.UnitTest
import com.netflix.spectator.api.NoopRegistry
import org.junit.experimental.categories.Category
import org.springframework.core.io.FileSystemResource
import org.springframework.retry.support.RetryTemplate
import spock.lang.Specification

/**
 * Unit tests for JobCompletionHandler
 *
 * @author amajumdar
 * @since 3.0.0
 */
@Category(UnitTest.class)
class JobCompletionServiceSpec extends Specification{
    private static final String NAME = UUID.randomUUID().toString();
    private static final String USER = UUID.randomUUID().toString();
    private static final String VERSION = UUID.randomUUID().toString();
    private static final String COMMAND_ARGS = UUID.randomUUID().toString();
    JobPersistenceService jobPersistenceService;
    JobSearchService jobSearchService;
    JobCompletionService jobCompletionService;
    MailService mailService;
    GenieFileTransferService genieFileTransferService;

    def setup(){
        jobPersistenceService = Mock(JobPersistenceService.class)
        jobSearchService = Mock(JobSearchService.class)
        mailService = Mock(MailService.class)
        genieFileTransferService = Mock(GenieFileTransferService.class)
        jobCompletionService = new JobCompletionService( jobPersistenceService, jobSearchService,
                genieFileTransferService, new FileSystemResource("/tmp"), mailService, new NoopRegistry(),
                false, false, false, new RetryTemplate())
    }

    def handleJobCompletion() throws Exception{
        given:
        def jobId = "1"
        when:
        jobCompletionService.handleJobCompletion(new JobFinishedEvent(jobId, JobFinishedReason.KILLED, "null", this))
        then:
        noExceptionThrown()
        3 * jobSearchService.getJob(jobId) >>
                { throw new GenieServerException("null")} >>
                { throw new GenieServerException("null")} >>
                { throw new GenieServerException("null")}
        when:
        jobCompletionService.handleJobCompletion(new JobFinishedEvent(jobId, JobFinishedReason.KILLED, "null", this))
        then:
        noExceptionThrown()
        1 * jobSearchService.getJob(jobId) >> new Job.Builder(NAME, USER, VERSION, COMMAND_ARGS)
                .withId(jobId).withStatus(JobStatus.SUCCEEDED).build();
        0 * jobPersistenceService.updateJobStatus(jobId,_,_)
        when:
        jobCompletionService.handleJobCompletion(new JobFinishedEvent(jobId, JobFinishedReason.KILLED, "null", this))
        then:
        noExceptionThrown()
        1 * jobSearchService.getJob(jobId) >> new Job.Builder(NAME, USER, VERSION, COMMAND_ARGS)
                .withId(jobId).withStatus(JobStatus.RUNNING).build();
        1 * jobPersistenceService.updateJobStatus(jobId,_,_)
    }
}
