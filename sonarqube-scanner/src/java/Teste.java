package uk.gov.hmcts.reform.idam.editor.spi.forgerock;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.idam.api.fr.idm.manage.user.model.IdmUser;
import uk.gov.hmcts.reform.idam.editor.spi.IdamAccountManagementService;
import uk.gov.hmcts.reform.idam.logging.Operation;
import uk.gov.hmcts.reform.idam.logging.OperationReporter;
import uk.gov.hmcts.reform.idam.spi.ServiceException;
import uk.gov.hmcts.reform.idam.spi.client.repository.RoleRepo;
import uk.gov.hmcts.reform.idam.spi.client.repository.UserRepo;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.idam.editor.spi.forgerock.UserRoleService.PreconditionFailure.ROLE_ALREADY_ASSIGNED;
import static uk.gov.hmcts.reform.idam.editor.spi.forgerock.UserRoleService.PreconditionFailure.ROLE_NOT_ASSIGNED;
import static uk.gov.hmcts.reform.idam.spi.ServiceException.Type.PRECONDITION_FAILED;
@Slf4j
@Service
public class AccountManagementService implements IdamAccountManagementService {
    private static final String REASON = "reason";
    private static final String ERROR_ROLES_NOT_EXIST = "One or more of the roles provided does not exist.";
    private static final String ERROR_ROLE_ALREADY_ASSIGNED = "One or more of the roles provided is already assigned to the user.";
    private static final String ERROR_ROLE_NOT_ASSIGNED = "The role provided is not assigned to the user.";
    public enum AccountManagementServiceOperation implements Operation {
        ACCOUNT_ROLE_ASSIGNMENT,
        ACCOUNT_ROLE_UNASSIGNMENT
    }
    private enum PreconditionFailure {
        NONEXISTENT_ROLE
    }
    private final OperationReporter operationReporter;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleService userRoleService;
    public AccountManagementService(OperationReporter operationReporter, UserRepo userRepo, RoleRepo roleRepo, UserRoleService userRoleService) {
        this.operationReporter = operationReporter;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleService = userRoleService;
    }
    /**
     * @should assign the roles to the user
     * @should throw service exception when role is already assigned
     * @should throw service exception on non-existing role
     */
    public void assignRolesToUser(final String username, final List<String> roleNames, final String clientId) {
        final IdmUser user = userRepo.getUserByUsername(username);
        final String userId = user.getId();
        final List<String> addRoleIds = roleNames.stream()
            .map(roleRepo::findRoleIdByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        if (addRoleIds.size() != roleNames.size()) {
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, PreconditionFailure.NONEXISTENT_ROLE.name().toLowerCase()));
            throw new ServiceException(PRECONDITION_FAILED, ERROR_ROLES_NOT_EXIST);
        }
        try {
            userRoleService.assignRolesToUserStrict(user, addRoleIds);
        } catch (ServiceException se) {
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, se.getMessage().toLowerCase()));
            if (ROLE_ALREADY_ASSIGNED.toString().equals(se.getMessage()) && PRECONDITION_FAILED.equals(se.getType())) {
                throw new ServiceException(PRECONDITION_FAILED, ERROR_ROLE_ALREADY_ASSIGNED);
            }
            throw se;
        }
        operationReporter.success(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId);
    }
    /**
     * @should unassign the role from the user
     * @should throw service exception when role is not assigned
     * @should throw service exception on non-existing role
     */
    public void unassignRoleFromUser(final String username, final String roleName, final String clientId) {
        final IdmUser user = userRepo.getUserByUsername(username);
        final String userId = user.getId();
        final Optional<String> roleId = roleRepo.findRoleIdByName(roleName);
        if (!roleId.isPresent()) {
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_UNASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, PreconditionFailure.NONEXISTENT_ROLE.name().toLowerCase()));
            throw new ServiceException(PRECONDITION_FAILED, ERROR_ROLES_NOT_EXIST);
        }
        try {
            userRoleService.unassignRoleFromUser(user, roleId.get());
        } catch (ServiceException se) {
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_UNASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, se.getMessage().toLowerCase()));
            if (ROLE_NOT_ASSIGNED.toString().equals(se.getMessage()) && PRECONDITION_FAILED.equals(se.getType())) {
                throw new ServiceException(PRECONDITION_FAILED, ERROR_ROLE_NOT_ASSIGNED);
            }
            throw se;
        }
        operationReporter.success(log, AccountManagementServiceOperation.ACCOUNT_ROLE_UNASSIGNMENT, clientId, userId);
    }
    /**
     * @should throw service exception if some roles do not exist
     * @should replace the roles for the user
     */
    public void replaceUserRoles(final String username, final List<String> roleNames, final String clientId){
        final IdmUser user = userRepo.getUserByUsername(username);
        final String userId = user.getId();
        final List<String> roleIds = roleNames.stream()
            .map(roleRepo::findRoleIdByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList());
        if(roleIds.size() != roleNames.size()){
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, PreconditionFailure.NONEXISTENT_ROLE.name().toLowerCase()));
            throw new ServiceException(ServiceException.Type.CLIENT_ERROR, ERROR_ROLES_NOT_EXIST);
        }
        try {
            userRoleService.replaceUserRoles(user, roleIds);
        } catch (ServiceException se) {
            operationReporter.report(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId, null, ImmutableMap.of(REASON, se.getMessage().toLowerCase()));
            throw se;
        }
        operationReporter.success(log, AccountManagementServiceOperation.ACCOUNT_ROLE_ASSIGNMENT, clientId, userId);
    }
}

