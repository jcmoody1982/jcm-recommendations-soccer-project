package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.core.mapper.TeamMapper;
import com.jcm.recommendations.soccer.core.repository.TeamRecentFormRepository;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamFormService {

    private final FootyStatsApiClient apiClient;
    private final TeamRecentFormRepository teamRecentFormRepository;
    private final TeamMapper teamMapper;

    @Transactional
    public void syncTeamForm(Long teamId) {
        log.info("Fetching recent form for team: teamId={}", teamId);

        TeamDto teamDto = apiClient.fetchTeamForm(teamId);
        
        if (teamDto == null) {
            log.warn("No form data returned for team: teamId={}", teamId);
            return;
        }

        TeamRecentForm form = teamMapper.toTeamRecentForm(teamDto);
        
        Optional<TeamRecentForm> existingForm = teamRecentFormRepository.findByTeamId(teamId);
        if (existingForm.isPresent()) {
            form.setId(existingForm.get().getId());
            log.info("Updating recent form for team: teamId={}", teamId);
        } else {
            log.info("Creating new recent form for team: teamId={}", teamId);
        }
        
        teamRecentFormRepository.save(form);
        log.info("Recent form persisted for team: teamId={}", teamId);
    }

    public TeamRecentForm getTeamRecentForm(Long teamId) {
        return teamRecentFormRepository.findByTeamId(teamId).orElse(null);
    }

    @Transactional
    public void deleteTeamForm(Long teamId) {
        teamRecentFormRepository.deleteByTeamId(teamId);
    }
}
