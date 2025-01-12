package com.example.jpa_one_to_one_unidirectional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Optional;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
class Customer  {
	@Id @GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	private String name;
}

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
class Credential  {
	@Id
	private Long id;
	private String password;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	private Customer customer;
}

record CustomerDTO(Long id, String name) {}
record CustomerWithCredentialDTO(String name, String password) {}
record EagerCredentialDTO(Long id, String password, Customer customer) {}
record LazyCredentialDTO(Long id, String password) {}

interface CustomerRepository extends JpaRepository<Customer, Long> {}

interface CredentialRepository extends JpaRepository<Credential, Long> {

	@Query("SELECT c FROM Credential c JOIN FETCH c.customer WHERE c.id = :id")
	Optional<Credential> findWithCustomerById(@Param("id") Long id);

	@Query("SELECT c FROM Credential c JOIN FETCH c.customer")
	List<Credential> findAllWithCustomer();
}

@Component
class CustomerAndCredentialMapper {

	public CustomerDTO toDto(Customer customer) {
		return new CustomerDTO(customer.getId(), customer.getName());
	}

	public Customer toCustomer(CustomerWithCredentialDTO dto) {
		return new Customer(null, dto.name());
	}

	public Credential toCredential(CustomerWithCredentialDTO dto) {
		return new Credential(null, dto.password(), null);
	}

	public <T> T toDto(Credential credential, boolean includeCustomer) {
		return includeCustomer
				? (T) new EagerCredentialDTO(credential.getId(), credential.getPassword(), credential.getCustomer())
				: (T) new LazyCredentialDTO(credential.getId(), credential.getPassword());
	}

}

@Service
@RequiredArgsConstructor
class CustomerService {
	private final CustomerAndCredentialMapper mapper;
	private final CustomerRepository customerRepository;
	private final CredentialRepository credentialRepository;

	private <T> T getOrThrow(Optional<T> optional) {
		return optional.orElseThrow(()
				-> new EntityNotFoundException("Entity not found!"));
	}

	@Transactional
	public void createCustomerWithCredential(CustomerWithCredentialDTO dto) {

		Customer customer = mapper.toCustomer(dto);
		Credential credential = mapper.toCredential(dto);
		credential.setCustomer(customer);

		customerRepository.save(customer);
		credentialRepository.save(credential);
	}

	public CustomerDTO getCustomerByID(Long id) {
		return mapper.toDto(getOrThrow(customerRepository.findById(id)));
	}

	public List<CustomerDTO> getAllCustomers() {
		return customerRepository.findAll()
				.stream().map(i -> mapper.toDto(i)).toList();
	}

	public <T> T getCredentialById(Long id, boolean includeCustomer) {
		Credential credential = includeCustomer
				? getOrThrow(credentialRepository.findWithCustomerById(id))
				: getOrThrow(credentialRepository.findById(id));
		return mapper.toDto(credential, includeCustomer);
	}

	public <T> List<T> getAllCredential(boolean includeCustomer) {
		List<Credential> credentials = includeCustomer
				? credentialRepository.findAllWithCustomer()
				: credentialRepository.findAll();

		return credentials.stream()
				.map(i -> (T) mapper.toDto(i, includeCustomer)).toList();
	}

	@Transactional
	public void update(Long id, CustomerWithCredentialDTO dto) {

		Customer updatedCustomer = getOrThrow(customerRepository.findById(id));
		Credential updatedCredential = getOrThrow(credentialRepository.findById(id));

		updatedCustomer.setName(dto.name());
		updatedCredential.setPassword(dto.password());

		customerRepository.save(updatedCustomer);
		credentialRepository.save(updatedCredential);
	}


	@Transactional
	public void deleteCustomerWithCredential(Long id) {
		credentialRepository.delete(getOrThrow(credentialRepository.findById(id)));
		customerRepository.delete(getOrThrow(customerRepository.findById(id)));
	}
}

@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
	@ExceptionHandler(EntityNotFoundException.class)
	private ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/customers")
class CustomerController {
	private final CustomerService service;

	@PostMapping
	public void createCustomer(@RequestBody CustomerWithCredentialDTO dto) {
		service.createCustomerWithCredential(dto);
	}

	@GetMapping("/{id}")
	public CustomerDTO getCustomer(@PathVariable Long id) {
		return service.getCustomerByID(id);
	}

	@GetMapping
	public List<CustomerDTO> getAllCustomers() {
		return service.getAllCustomers();
	}

	@PutMapping("/{id}")
	public void updateCustomer(@PathVariable Long id, @RequestBody CustomerWithCredentialDTO dto) {
		service.update(id, dto);
	}

	@DeleteMapping("/{id}")
	public void deleteCustomer(@PathVariable Long id) {
		service.deleteCustomerWithCredential(id);
	}
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/credentials")
class CredentialController {
	private final CustomerService service;

	@GetMapping("/{id}")
	public <T> T getCredential(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeCustomer) {
		return service.getCredentialById(id, includeCustomer);
	}

	@GetMapping
	public <T> List<T> getAllCredentials(@RequestParam(defaultValue = "false") boolean includeCustomer) {
		return service.getAllCredential(includeCustomer);
	}
}

@SpringBootApplication
public class JpaOneToOneUnidirectionalApplication {
	public static void main(String[] args) {
		SpringApplication.run(JpaOneToOneUnidirectionalApplication.class, args);
	}
	@Bean
	public CommandLineRunner run(CustomerService service, CustomerRepository customerRepository, CredentialRepository credentialRepository) {
		return args -> {

			service.createCustomerWithCredential(
					new CustomerWithCredentialDTO("jack", "asd")
			);

			service.createCustomerWithCredential(
					new CustomerWithCredentialDTO("ann", "zxc")
			);
		};
	}
}

/*curl -X POST http://localhost:8080/customers \
		-H "Content-Type: application/json" \
		-d '{"name": "jack", "password": "asd"}'*/

//curl http://localhost:8080/customers/{id}

//curl http://localhost:8080/customers

/*curl -X PUT http://localhost:8080/customers/{id} \
		-H "Content-Type: application/json" \
		-d '{"name": "new name", "password": "new password"}'*/

//curl -X DELETE http://localhost:8080/customers/{id}

//curl "http://localhost:8081/credentials/3?includeCustomer=true"

//curl "http://localhost:8081/credentials?includeCustomer=false"


