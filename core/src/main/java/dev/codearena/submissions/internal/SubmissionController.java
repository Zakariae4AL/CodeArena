package dev.codearena.submissions.internal;

import dev.codearena.shared.CurrentUser;
import dev.codearena.shared.Language;
import dev.codearena.submissions.internal.SubmissionService.SubmissionView;
import dev.codearena.submissions.internal.SubmissionService.SubmitRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SubmissionController {

    record LanguageView(String id, String label) {
    }

    private final SubmissionService service;

    SubmissionController(SubmissionService service) {
        this.service = service;
    }

    /** Accepted for judging: the verdict arrives later over WebSocket (or by polling GET). */
    @PostMapping("/api/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SubmissionView submit(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SubmitRequest request) {
        return service.submit(user, request);
    }

    @GetMapping("/api/submissions/{id}")
    SubmissionView get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    @GetMapping("/api/submissions/mine")
    List<SubmissionView> mine(@AuthenticationPrincipal CurrentUser user,
                              @RequestParam(required = false) Long problemId,
                              @RequestParam(defaultValue = "50") int limit) {
        return service.mine(user, problemId, limit);
    }

    @GetMapping("/api/submissions/solved")
    List<Long> solved(@AuthenticationPrincipal CurrentUser user) {
        return service.solved(user);
    }

    @GetMapping("/api/languages")
    List<LanguageView> languages() {
        return Arrays.stream(Language.values()).map(l -> new LanguageView(l.id(), l.label())).toList();
    }
}
