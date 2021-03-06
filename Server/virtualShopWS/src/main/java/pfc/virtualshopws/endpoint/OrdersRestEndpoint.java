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

import com.google.gson.Gson;

import pfc.virtualshopws.entity.OrderDetails;
import pfc.virtualshopws.entity.Orders;
import pfc.virtualshopws.entity.PaymentDetails;
import pfc.virtualshopws.service.OrderDetailsService;
import pfc.virtualshopws.service.OrdersService;
import pfc.virtualshopws.service.PaymentDetailsService;

@Controller
@Path("orders")
public class OrdersRestEndpoint {
	@Autowired
	private OrdersService ordersService;
	@Autowired
	private OrderDetailsService orderDetailsService;
	@Autowired
	private PaymentDetailsService paymentDetailsService;

	@GET
	@Path("/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Orders findById(@PathParam("id") Long id) {

		return ordersService.findById(id);

	}

	@GET
	@Path("/response/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response findByIdResponse(@PathParam("id") Long id) {

		Orders order = ordersService.findById(id);

		return Response.status(HttpStatus.ACCEPTED.value()).entity(order).build();

	}

	@GET
	@Path("")
	@Produces("application/json")
	@Consumes("application/json")
	public List<Orders> findAll() {

		return ordersService.findAll();

	}

	@Transactional
	@POST
	@Path("")
	@Produces("application/json")
	@Consumes("application/json")
	public Orders create(@RequestParam Orders order) {

		return ordersService.create(order);

	}

	@Transactional
	@PUT
	@Path("{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Orders update(@RequestParam Orders order, @PathParam("id") Long id) {

		Orders orderRecovered = ordersService.findById(id);
		order.setOrderId(id);
		BeanUtils.copyProperties(order, orderRecovered);

		return ordersService.update(orderRecovered);

	}

	@Transactional
	@DELETE
	@Path("{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response delete(@PathParam("id") Long id) {
		Orders orderRecovered = ordersService.findById(id);

		ordersService.delete(orderRecovered);

		return Response.status(HttpStatus.ACCEPTED.value()).build();

	}

	// ########## MÉTODOS AGREGADOS ##########
	@Transactional
	@POST
	@Path("confirm")
	@Produces("application/json")
	@Consumes("application/json")
	public Response confirm(@RequestParam Orders order, @Context HttpServletRequest req) {

		String ssoTokenReqId = req.getHeader("Authorization");

		if (SSOTokenMap.INSTANCE.getUser(ssoTokenReqId) != null) {
			// si el user está logueado, renovamos la sesión
			Date expirationTime = new Date();
			expirationTime.setTime(expirationTime.getTime() + (3600 * 1000));
			// #######ORDER#######
			long userId = (SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId)).getUserId();

			Orders orderInfo = new Orders(userId, order.getTotal());
			Orders orderCreated = ordersService.create(orderInfo);

			long orderId = orderCreated.getOrderId();

			// #######PAYMENT#######
			PaymentDetails paymentDetails = order.getPaymentDetail();
			paymentDetails.setOrderId(orderId);
			paymentDetailsService.create(paymentDetails);

			// #######ORDERDETAILS#######
			for (OrderDetails orderDetails : order.getOrderDetails()) {

				orderDetails.setOrderId(orderId);
				orderDetailsService.create(orderDetails);
			}
			return Response.status(HttpStatus.CREATED.value()).build();
		} else {
			return Response.status(HttpStatus.NOT_FOUND.value()).build();
		}

	}

	@Transactional
	@DELETE
	@Path("orderId/{id}")
	@Produces("application/json")
	@Consumes("application/json")
	public Response deleteOrderRoots(@PathParam("id") Long orderId, @Context HttpServletRequest req) {
		String ssoTokenReqId = req.getHeader("Authorization");
		if (orderId != null) {
			if (SSOTokenMap.INSTANCE.getUser(ssoTokenReqId) != null) {
				// si el user está logueado, renovamos la sesión
				Date expirationTime = new Date();
				expirationTime.setTime(expirationTime.getTime() + (3600 * 1000));
				SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId).setExpiration(expirationTime);
				// Borramos el pedido, los detalles y los detalles de pago del
				// pedido
				Orders orderRecovered = ordersService.findById(orderId);

				orderDetailsService.deleteByOrderId(orderId);
				ordersService.delete(orderRecovered);
				paymentDetailsService.deleteByOrderId(orderId);
				return Response.status(HttpStatus.ACCEPTED.value()).build();
			} else {
				return Response.status(HttpStatus.FORBIDDEN.value()).build();
			}
		} else {
			return Response.status(HttpStatus.ACCEPTED.value()).build();
		}

	}

	@GET
	@Path("/userId/")
	@Produces("application/json")
	@Consumes("application/json")
	public Response findOrdersByUserId(@Context HttpServletRequest req) {
		String ssoTokenReqId = req.getHeader("Authorization");

		if (SSOTokenMap.INSTANCE.getUser(ssoTokenReqId) != null) {
			long userId = (SSOTokenMap.INSTANCE.getSSOToken(ssoTokenReqId)).getUserId();

			if (ordersService.findOrdersByUserId(userId) != null) {
				String jsonResponse = new Gson().toJson(ordersService.findOrdersByUserId(userId));

				System.out.println(jsonResponse);
				return Response.status(HttpStatus.OK.value()).entity(jsonResponse).build();
			} else {
				return Response.status(HttpStatus.NOT_FOUND.value()).build();
			}
		} else {
			return Response.status(HttpStatus.FORBIDDEN.value()).build();
		}

	}

}
