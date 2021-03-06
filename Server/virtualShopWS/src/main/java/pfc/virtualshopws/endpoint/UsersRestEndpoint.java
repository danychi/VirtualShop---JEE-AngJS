package pfc.virtualshopws.endpoint;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import pfc.virtualshopws.dto.UsersDto;
import pfc.virtualshopws.entity.Users;
import pfc.virtualshopws.service.UsersService;

@Controller
@Path("users")
public class UsersRestEndpoint {

	@Autowired
	private UsersService usersService;

	@GET
	@Path("/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Users findById(@PathParam("id") Long id) {
		return usersService.findById(id);
	}

	@GET
	@Path("/username/{username}")
	@Produces("application/json")
	@Consumes("application/json")
	public Users findUserByUsername(@PathParam("username") String username) {
		return usersService.findUserByUsername(username);
	}

	@GET
	@Path("/response/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response findByIdResponse(@PathParam("id") Long id) {
		Users user = usersService.findById(id);
		return Response.status(HttpStatus.ACCEPTED.value()).entity(user).build();
	}

	@GET
	@Path("")
	@Produces("application/json")
	@Consumes("application/json")
	public List<Users> findAll() {
		return usersService.findAll();
	}

	@Transactional
	@DELETE
	@Path("{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Boolean delete(@PathParam("id") Long id) {
		Users userRecovered = usersService.findById(id);
		usersService.delete(userRecovered);
		return true;
	}

	// ########## MÉTODOS AGREGADOS ##########
	@Transactional
	@POST
	@Path("/signup")
	@Produces("application/json")
	@Consumes("application/json")
	public Response create(@RequestParam Users user) {
		// Create the user and insert into the DB
		usersService.create(user);
		Date expirationTime = new Date();
		expirationTime.setTime(expirationTime.getTime() + (3600 * 1000));

		UsersDto userDto = new UsersDto(user.getEmail(), user.getPassword());
		Long userId = (usersService.findUserByEmail(userDto.getEmail())).getUserId();
		SSOToken ssoToken = new SSOToken(userDto, expirationTime, userId);
		SSOTokenMap.INSTANCE.addSSOToken(ssoToken);

		String token = ssoToken.getTokenId();
		System.out.println(token);

		Response response = Response.status(HttpStatus.CREATED.value())
				.header("Access-Control-Allow-Origin", "http://localhost:8080")
				.header("Access-Control-Allow-Credentials", true)
				.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
				.header("Access-Control-Allow-Headers", "Content-Type, authorization").header("token", token)
				.entity(token).build();
		return response;
	}

	@POST
	@Path("/signin")
	@Produces("application/json")
	@Consumes("application/json")
	public Response signin(@RequestParam UsersDto userDto, @Context HttpServletRequest req) {
		String ssoTokenReqId = req.getHeader("Authorization");

		Users user = usersService.findUserByEmail(userDto.getEmail());
		String pass = userDto.getPassword();
		Long userId = user.getUserId();

		Date expirationTime = new Date();
		expirationTime.setTime(expirationTime.getTime() + (3600 * 1000));

		if (pass.equals(user.getPassword())) {
			if (ssoTokenReqId == null) {
				// Creamos nuevo Token si se pasa el token vacio o no existe
				// token asignado al user
				SSOToken ssoToken = new SSOToken(userDto, expirationTime, userId);
				SSOTokenMap.INSTANCE.addSSOToken(ssoToken);

				String token = ssoToken.getTokenId();
				System.out.println(token);

				Response response = Response.status(200).header("Access-Control-Allow-Origin", "http://localhost:8080")
						.header("Access-Control-Allow-Credentials", true)
						.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
						.header("Access-Control-Allow-Headers", "Content-Type, authorization").header("token", token)
						.entity(token).build();
				return response;
			} else {
				if (SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId) == null) {
					// Si el token no está en nuestro singleton, le devolvemos
					// un token nuevo
					SSOToken ssoToken = new SSOToken(userDto, expirationTime, userId);
					SSOTokenMap.INSTANCE.addSSOToken(ssoToken);

					String token = ssoToken.getTokenId();
					System.out.println(token);

					Response response = Response.status(200)
							.header("Access-Control-Allow-Origin", "http://localhost:8080")
							.header("Access-Control-Allow-Credentials", true)
							.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
							.header("Access-Control-Allow-Headers", "Content-Type, authorization")
							.header("token", token).entity(token).build();
					return response;
				} else {
					// Si el token del usuario se encuentra en nuestro singleton
					// reiciniamos el tiempo de sesión
					SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId).setExpiration(expirationTime);

					Response response = Response.status(200)
							.header("Access-Control-Allow-Origin", "http://localhost:8080")
							.header("Access-Control-Allow-Credentials", true)
							.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
							.header("Access-Control-Allow-Headers", "Content-Type, authorization")
							.header("token", ssoTokenReqId).entity(ssoTokenReqId).build();
					return response;
				}
			}
		} else {
			// usuario no encontrado
			return Response.status(HttpStatus.UNAUTHORIZED.value()).entity(user).build();
		}
	}

	@Transactional
	@PUT
	@Path("/update")
	@Produces("application/json")
	@Consumes("application/json")
	public Response update(@RequestParam Users user, @Context HttpServletRequest req) {
		String ssoTokenReqId = req.getHeader("Authorization");

		if (ssoTokenReqId != null) {
			if (SSOTokenMap.INSTANCE.getUser(ssoTokenReqId) != null) {
				UsersDto userDto = SSOTokenMap.INSTANCE.getUser(ssoTokenReqId);
				Users userRecovered = usersService.findUserByEmail(userDto.getEmail());
				user.setUserId((userRecovered.getUserId()));

				BeanUtils.copyProperties(user, userRecovered);
				usersService.update(userRecovered);

				Response response = Response.status(HttpStatus.ACCEPTED.value())
						.header("Access-Control-Allow-Origin", "http://localhost:8080")
						.header("Access-Control-Allow-Credentials", true)
						.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
						.header("Access-Control-Allow-Headers", "Content-Type, authorization")
						.header("token", ssoTokenReqId).entity(ssoTokenReqId).build();
				return response;
			} else {
				return Response.status(HttpStatus.UNAUTHORIZED.value()).build();
			}
		} else {
			return Response.status(HttpStatus.UNAUTHORIZED.value()).build();
		}
	}

	@GET
	@Path("/getUserInfo")
	@Produces("application/json")
	@Consumes("application/json")
	public Response getUserInfo(@Context HttpServletRequest req) {
		String ssoTokenReqId = req.getHeader("Authorization");

		if (ssoTokenReqId != null) {
			if (SSOTokenMap.INSTANCE.getUser(ssoTokenReqId) != null) {
				// si el user está logueado, renovamos la sesión
				Date expirationTime = new Date();
				expirationTime.setTime(expirationTime.getTime() + (3600 * 1000));
				SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId).setExpiration(expirationTime);
				// Recuperamos la info del User según su token
				UsersDto userDto = SSOTokenMap.INSTANCE.getUser(ssoTokenReqId);

				Users user = usersService.findUserByEmail(userDto.getEmail());
				System.out.println(user);

				Response response = Response.status(HttpStatus.ACCEPTED.value())
						.header("Access-Control-Allow-Origin", "http://localhost:8080")
						.header("Access-Control-Allow-Credentials", true)
						.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE,HEAD")
						.header("Access-Control-Allow-Headers", "Content-Type, authorization").entity(user).build();
				return response;
			} else {
				return Response.status(HttpStatus.UNAUTHORIZED.value()).build();
			}
		} else {
			return Response.status(HttpStatus.UNAUTHORIZED.value()).build();
		}
	}

}
