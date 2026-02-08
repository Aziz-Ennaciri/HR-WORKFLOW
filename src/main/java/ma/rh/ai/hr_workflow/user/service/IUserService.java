package ma.rh.ai.hr_workflow.user.service;

import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;

import java.util.List;

public interface IUserService {
    UserResponseDTO register(CreateUserDTO dto);

    LoginResponseDTO login(LoginRequestDTO dto);

    UserResponseDTO updateUser(Long id ,CreateUserDTO dto);

    UserResponseDTO getUserById(Long id);

    List<UserResponseDTO> getAllUsers();

    void disableSer(Long id);
}
