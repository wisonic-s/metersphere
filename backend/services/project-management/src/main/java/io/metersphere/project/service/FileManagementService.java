package io.metersphere.project.service;

import io.metersphere.project.domain.*;
import io.metersphere.project.dto.filemanagement.FileManagementQuery;
import io.metersphere.project.dto.filemanagement.request.FileBatchProcessRequest;
import io.metersphere.project.mapper.*;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.TempFileUtils;
import io.metersphere.system.file.FileRequest;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class FileManagementService {
    @Resource
    private FileMetadataMapper fileMetadataMapper;
    @Resource
    private FileMetadataRepositoryMapper fileMetadataRepositoryMapper;
    @Resource
    private FileAssociationMapper fileAssociationMapper;
    @Resource
    private FileModuleMapper fileModuleMapper;
    @Resource
    private FileService fileService;
    @Resource
    private FileMetadataLogService fileMetadataLogService;
    @Resource
    private ExtFileMetadataMapper extFileMetadataMapper;

    public void checkModule(String moduleId, String nodeTypeDefault) {
        if (!StringUtils.equals(moduleId, ModuleConstants.DEFAULT_NODE_ID)) {
            FileModuleExample example = new FileModuleExample();
            example.createCriteria().andIdEqualTo(moduleId).andModuleTypeEqualTo(nodeTypeDefault);
            if (fileModuleMapper.countByExample(example) == 0) {
                throw new MSException("file_module.not.exist");
            }
        }
    }

    public void delete(FileBatchProcessRequest request, String operator) {
        List<FileMetadata> deleteList = this.getDeleteList(request);
        List<String> deleteIds = deleteList.stream().map(FileMetadata::getId).toList();
        if (CollectionUtils.isNotEmpty(deleteIds)) {
            FileMetadataExample example = new FileMetadataExample();
            example.createCriteria().andIdIn(deleteIds);
            fileMetadataMapper.deleteByExample(example);

            FileMetadataRepositoryExample repositoryExample = new FileMetadataRepositoryExample();
            repositoryExample.createCriteria().andFileMetadataIdIn(deleteIds);
            fileMetadataRepositoryMapper.deleteByExample(repositoryExample);

            FileAssociationExample associationExample = new FileAssociationExample();
            associationExample.createCriteria().andFileIdIn(deleteIds);
            fileAssociationMapper.deleteByExample(associationExample);

            //记录日志
            fileMetadataLogService.saveDeleteLog(deleteList, request.getProjectId(), operator);

            deleteList.forEach(fileMetadata -> {
                FileRequest fileRequest = new FileRequest();
                fileRequest.setFileName(fileMetadata.getId());
                fileRequest.setProjectId(fileMetadata.getProjectId());
                fileRequest.setStorage(fileMetadata.getStorage());
                try {
                    //删除存储容器中的文件
                    fileService.deleteFile(fileRequest);
                    //删除临时文件
                    TempFileUtils.deleteTmpFile(fileMetadata.getId());
                } catch (Exception e) {
                    LogUtils.error("删除文件失败", e);
                }
            });
        }
    }

    public List<FileMetadata> getDeleteList(FileBatchProcessRequest request) {
        List<String> processIds = request.getSelectIds();
        List<FileMetadata> refFileList = new ArrayList<>();
        FileManagementQuery pageDTO = new FileManagementQuery(request);
        if (request.isSelectAll()) {
            refFileList = extFileMetadataMapper.selectRefIdByKeywordAndFileType(pageDTO);
            if (CollectionUtils.isNotEmpty(request.getExcludeIds())) {
                refFileList = refFileList.stream().filter(fileMetadata -> !request.getExcludeIds().contains(fileMetadata.getId())).toList();
            }
        } else if (CollectionUtils.isNotEmpty(processIds)) {
            refFileList = extFileMetadataMapper.selectRefIdByIds(processIds);
        }

        List<String> refIdList = refFileList.stream().map(FileMetadata::getRefId).toList();
        if (CollectionUtils.isNotEmpty(refIdList)) {
            processIds = extFileMetadataMapper.selectIdByRefIdList(refIdList);
            return extFileMetadataMapper.selectDeleteFileInfoByIds(processIds);
        } else {
            return new ArrayList<>();
        }
    }

    public List<FileMetadata> getProcessList(FileBatchProcessRequest request) {
        List<String> processIds = request.getSelectIds();
        List<FileMetadata> processFileList = new ArrayList<>();
        if (request.isSelectAll()) {
            FileManagementQuery pageDTO = new FileManagementQuery(request);
            processFileList = extFileMetadataMapper.selectByKeywordAndFileType(pageDTO);
            //去除未选择的文件
            if (CollectionUtils.isNotEmpty(request.getExcludeIds())) {
                processFileList = processFileList.stream().filter(fileMetadata -> !request.getExcludeIds().contains(fileMetadata.getId())).toList();
            }
        } else if (CollectionUtils.isNotEmpty(processIds)) {
            FileMetadataExample example = new FileMetadataExample();
            example.createCriteria().andIdIn(processIds);
            processFileList = fileMetadataMapper.selectByExample(example);
        }

        return processFileList;
    }

    public void deleteByModuleIds(List<String> deleteModuleIds) {
        //获取要删除的文件引用ID
        List<FileMetadata> refFileList = extFileMetadataMapper.selectRefIdByModuleIds(deleteModuleIds);
        List<String> refIdList = refFileList.stream().map(FileMetadata::getRefId).toList();
        if (CollectionUtils.isNotEmpty(refIdList)) {
            //获取要删除的所有文件ID
            List<FileMetadata> deleteList = extFileMetadataMapper.selectDeleteFileInfoByRefIdList(refIdList);
            if (CollectionUtils.isNotEmpty(deleteList)) {
                FileMetadataExample example = new FileMetadataExample();
                example.createCriteria().andIdIn(
                        deleteList.stream().map(FileMetadata::getId).toList());
                fileMetadataMapper.deleteByExample(example);

                deleteList.forEach(fileMetadata -> {
                    FileRequest fileRequest = new FileRequest();
                    fileRequest.setFileName(fileMetadata.getId());
                    fileRequest.setProjectId(fileMetadata.getProjectId());
                    fileRequest.setStorage(fileMetadata.getStorage());
                    try {
                        fileService.deleteFile(fileRequest);
                    } catch (Exception e) {
                        LogUtils.error("删除文件失败", e);
                    }
                });
            }
        }
    }
}