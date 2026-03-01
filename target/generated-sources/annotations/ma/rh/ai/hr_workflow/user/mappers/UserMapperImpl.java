package ma.rh.ai.hr_workflow.user.mappers;

import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import ma.rh.ai.hr_workflow.user.model.User;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:15+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponseDTO toResponseDTO(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponseDTO userResponseDTO = new UserResponseDTO();

        userResponseDTO.setId( user.getId() );
        userResponseDTO.setEmail( user.getEmail() );
        userResponseDTO.setFirstName( user.getFirstName() );
        userResponseDTO.setLastName( user.getLastName() );

        userResponseDTO.setRoles( user.getRoles().stream().map(role -> role.getName().name()).collect(java.util.stream.Collectors.toSet()) );

        return userResponseDTO;
    }
}
