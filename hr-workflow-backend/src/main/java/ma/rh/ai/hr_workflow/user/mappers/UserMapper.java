package ma.rh.ai.hr_workflow.user.mappers;

import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import ma.rh.ai.hr_workflow.user.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(role -> role.getName().name()).collect(java.util.stream.Collectors.toSet()))")
    @Mapping(target = "theme", source = "theme")
    @Mapping(target = "language", source = "language")
    @Mapping(target = "emailNotificationsEnabled", source = "emailNotificationsEnabled")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    UserResponseDTO toResponseDTO(User user);
}