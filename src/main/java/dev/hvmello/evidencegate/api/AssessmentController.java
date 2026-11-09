package dev.hvmello.evidencegate.api;

import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.domain.Assessment;
import dev.hvmello.evidencegate.domain.Conversation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssessmentController {

    private final AssessmentPipeline pipeline;

    AssessmentController(AssessmentPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/v1/assessments")
    Assessment assess(@RequestBody Conversation conversation) {
        return pipeline.assess(conversation);
    }
}
