package io.metersphere.bug.service;

import io.metersphere.bug.dto.request.BugSyncRequest;
import io.metersphere.project.domain.Project;

/**
 * 缺陷Xpack功能接口 (全量同步)
 */
public interface XpackBugService {

    /**
     * 同步系统下所有第三方平台项目缺陷(定时任务)
     */
    void syncPlatformBugsBySchedule();

    /**
     * 同步当前项目第三方平台缺陷(前台调用)
     * @param project 项目
     * @param request 同步请求参数
     * @param currentUser 当前用户
     */
    void syncPlatformBugs(Project project, BugSyncRequest request, String currentUser);
}