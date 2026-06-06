package ma.rh.ai.hr_workflow.user.service;

import ma.rh.ai.hr_workflow.user.DTOs.ChangeEmailDTO;
import ma.rh.ai.hr_workflow.user.DTOs.ChangePasswordDTO;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdatePreferencesDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UpdateProfileDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;

import java.util.List;

public interface IUserService {
    UserResponseDTO register(CreateUserDTO dto);

    LoginResponseDTO login(LoginRequestDTO dto);

    UserResponseDTO updateUser(Long id, CreateUserDTO dto);

    UserResponseDTO getUserById(Long id);

    List<UserResponseDTO> getAllUsers();

    void disableUser(Long id);

    UserResponseDTO getMe(String email);

    UserResponseDTO updateProfile(String email, UpdateProfileDTO dto);

    UserResponseDTO changeEmail(String email, ChangeEmailDTO dto);

    void changePassword(String email, ChangePasswordDTO dto);

    UserResponseDTO updatePreferences(String email, UpdatePreferencesDTO dto);

    void deleteMe(String email);
}
