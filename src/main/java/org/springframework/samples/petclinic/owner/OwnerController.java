/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Wick Dynex
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	// Maximum dataset size to prevent unbounded memory growth in benchmarks
	private static final int MAX_DATASET_SIZE = 20_000_000; // 20M owners max

	private final OwnerRepository owners;

	private final PetTypeRepository petTypes;

	private final Random random = new Random();

	// Thread-safe response time statistics for P95/P99 calculation
	private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Long>> threadResponseTimes = new ConcurrentHashMap<>();

	private final AtomicLong totalRequests = new AtomicLong(0);

	private static final int MAX_SAMPLES_PER_THREAD = 10_000; // Keep last 10K samples per
																// thread to avoid memory
																// issues

	public OwnerController(OwnerRepository owners, PetTypeRepository petTypes) {
		this.owners = owners;
		this.petTypes = petTypes;
	}

	// Response time statistics result
	public static class ResponseTimeStats {

		private final long totalRequests;

		private final long p50; // 50th percentile (median)

		private final long p95; // 95th percentile

		private final long p99; // 99th percentile

		private final long min;

		private final long max;

		private final double avg;

		public ResponseTimeStats(long totalRequests, long p50, long p95, long p99, long min, long max, double avg) {
			this.totalRequests = totalRequests;
			this.p50 = p50;
			this.p95 = p95;
			this.p99 = p99;
			this.min = min;
			this.max = max;
			this.avg = avg;
		}

		// Getters
		public long getTotalRequests() {
			return totalRequests;
		}

		public long getP50() {
			return p50;
		}

		public long getP95() {
			return p95;
		}

		public long getP99() {
			return p99;
		}

		public long getMin() {
			return min;
		}

		public long getMax() {
			return max;
		}

		public double getAvg() {
			return avg;
		}

	}

	// Record response time for statistics (thread-safe)
	private void recordResponseTime(long responseTimeMicros) {
		long threadId = Thread.currentThread().getId();
		ConcurrentLinkedQueue<Long> threadSamples = threadResponseTimes.computeIfAbsent(threadId,
				k -> new ConcurrentLinkedQueue<>());
		threadSamples.offer(responseTimeMicros);
		totalRequests.incrementAndGet();

		// Keep only the most recent samples per thread to avoid memory issues
		while (threadSamples.size() > MAX_SAMPLES_PER_THREAD) {
			threadSamples.poll();
		}
	}

	// Calculate percentiles from collected response times across all threads
	private ResponseTimeStats calculateStats() {
		List<Long> allSamples = new ArrayList<>();

		// Collect samples from all threads
		for (ConcurrentLinkedQueue<Long> threadSamples : threadResponseTimes.values()) {
			if (threadSamples != null && !threadSamples.isEmpty()) {
				allSamples.addAll(threadSamples);
			}
		}

		if (allSamples.isEmpty()) {
			return new ResponseTimeStats(0, 0, 0, 0, 0, 0, 0.0);
		}

		Collections.sort(allSamples);
		int n = allSamples.size();

		long min = allSamples.get(0);
		long max = allSamples.get(n - 1);
		long p50 = getPercentile(allSamples, 50);
		long p95 = getPercentile(allSamples, 95);
		long p99 = getPercentile(allSamples, 99);

		double avg = allSamples.stream().mapToLong(Long::longValue).average().orElse(0.0);

		return new ResponseTimeStats(totalRequests.get(), p50, p95, p99, min, max, avg);
	}

	// Clear all collected response time statistics
	private void clearResponseTimeStats() {
		threadResponseTimes.clear();
		totalRequests.set(0);
	}

	// Get percentile value from sorted list
	private long getPercentile(List<Long> sortedSamples, int percentile) {
		if (sortedSamples.isEmpty())
			return 0;

		int n = sortedSamples.size();
		double index = (percentile / 100.0) * (n - 1);
		int lower = (int) Math.floor(index);
		int upper = (int) Math.ceil(index);

		if (lower == upper) {
			return sortedSamples.get(lower);
		}

		// Linear interpolation between two values
		long lowerValue = sortedSamples.get(lower);
		long upperValue = sortedSamples.get(upper);
		return lowerValue + (long) ((upperValue - lowerValue) * (index - lower));
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		if (ownerId == null) {
			return new Owner();
		}

		// For API requests or DELETE requests, don't throw exception if owner doesn't
		// exist
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
		String requestURI = attrs.getRequest().getRequestURI();
		if ("DELETE".equals(attrs.getRequest().getMethod()) || requestURI.contains("/api/")) {
			return this.owners.findById(ownerId).orElse(null);
		}

		return this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId
					+ ". Please ensure the ID is correct " + "and the owner exists in the database."));
	}

	@GetMapping("/owners/new")
	public String initCreationForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		return "redirect:/owners/" + owner.getId();
	}

	/**
	 * API endpoint to get the total count of owners
	 * @return the total number of owners in the system
	 */
	@GetMapping("/api/owners/count")
	public @ResponseBody ResponseEntity<Long> getOwnerCount() {
		long startTime = System.nanoTime();
		try {
			long count = this.owners.count();
			long responseTime = (System.nanoTime() - startTime) / 1000; // Convert to
																		// microseconds
			recordResponseTime(responseTime);
			return ResponseEntity.ok(count);
		}
		catch (Exception e) {
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			throw e;
		}
	}

	/**
	 * API endpoint to get response time statistics (P50, P95, P99)
	 * @return response time statistics including percentiles
	 */
	@GetMapping("/api/owners/stats")
	public @ResponseBody ResponseEntity<ResponseTimeStats> getResponseTimeStats() {
		long startTime = System.nanoTime();
		try {
			ResponseTimeStats stats = calculateStats();
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			return ResponseEntity.ok(stats);
		}
		catch (Exception e) {
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			throw e;
		}
	}

	/**
	 * API endpoint to clear response time statistics
	 * @return confirmation message
	 */
	@PostMapping("/api/owners/stats/clear")
	public @ResponseBody ResponseEntity<String> clearResponseTimeStatsApi() {
		clearResponseTimeStats();
		return ResponseEntity.ok("Response time statistics cleared");
	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		// allow parameterless GET request for /owners to return all records
		String lastName = owner.getLastName();
		if (lastName == null) {
			lastName = ""; // empty string signifies broadest possible search
		}

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, lastName);
		if (ownersResults.isEmpty()) {
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}

		if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			return "redirect:/owners/" + owner.getId();
		}

		// multiple owners found
		return addPaginationModel(page, model, ownersResults);
	}

	/**
	 * REST API endpoint to get a specific owner with their pets as JSON
	 * @param ownerId the ID of the owner to retrieve
	 * @return the owner with their pets
	 */
	@GetMapping("/api/owners/{ownerId}")
	public @ResponseBody ResponseEntity<Owner> getOwnerWithPetsApi(@PathVariable("ownerId") int ownerId) {
		long startTime = System.nanoTime();
		try {
			Optional<Owner> optionalOwner = this.owners.findById(ownerId);
			ResponseEntity<Owner> response;
			if (optionalOwner.isPresent()) {
				Owner owner = optionalOwner.get();
				response = ResponseEntity.ok(owner);
			}
			else {
				response = ResponseEntity.notFound().build();
			}
			long responseTime = (System.nanoTime() - startTime) / 1000; // Convert to
																		// microseconds
			recordResponseTime(responseTime);
			return response;
		}
		catch (Exception e) {
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			throw e;
		}
	}

	/**
	 * REST API endpoint to create a new owner with random pets (1-10 pets)
	 * @param owner the owner to create (without pets)
	 * @return the created owner with randomly generated pets
	 */
	@PostMapping("/api/owners/batch")
	public @ResponseBody ResponseEntity<?> createOwnersBatchApi(@Valid @RequestBody List<Owner> owners,
			BindingResult result) {
		if (result.hasErrors()) {
			return ResponseEntity.badRequest().body("Invalid owner data: " + result.getAllErrors());
		}

		// Check dataset size limit to prevent unbounded memory growth
		long currentSize = this.owners.count();
		if (currentSize + owners.size() > MAX_DATASET_SIZE) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("error",
						"Dataset size limit would be exceeded (" + MAX_DATASET_SIZE + " owners max). "
								+ "Cannot create " + owners.size() + " more owners (current: " + currentSize + "). "
								+ "Consider deleting some owners first or restarting the application."));
		}

		List<Integer> createdIds = new ArrayList<>();
		for (Owner owner : owners) {
			// Generate random number of pets (1-3, optimized for memory)
			int numberOfPets = random.nextInt(3) + 1;

			// Get all available pet types
			List<PetType> availableTypes = petTypes.findPetTypes();

			// Create random pets for the owner (optimized for memory)
			for (int i = 0; i < numberOfPets; i++) {
				Pet pet = new Pet();
				// Shortened pet name to reduce memory
				pet.setName("P" + (i + 1) + "-" + owner.getLastName().charAt(0));

				// Random birth date between 1-5 years ago (reduced range)
				int yearsAgo = random.nextInt(5) + 1;
				LocalDate birthDate = LocalDate.now().minusYears(yearsAgo).minusDays(random.nextInt(365));
				pet.setBirthDate(birthDate);

				// Random pet type
				PetType randomType = availableTypes.get(random.nextInt(availableTypes.size()));
				pet.setType(randomType);

				// Clear visits to reduce memory (no visits needed for memory test)
				pet.getVisits().clear();

				// Add pet to owner
				owner.addPet(pet);
			}

			Owner savedOwner = this.owners.save(owner);
			createdIds.add(savedOwner.getId());
		}

		return ResponseEntity.ok(Map.of("createdIds", createdIds, "count", createdIds.size()));
	}

	@PostMapping("/api/owners/generate/{count}")
	public @ResponseBody ResponseEntity<?> generateOwnersInMemory(@PathVariable int count) {
		long startTime = System.currentTimeMillis();

		// Special case: count=0 means clear all data
		if (count == 0) {
			this.owners.deleteAll();
			long endTime = System.currentTimeMillis();
			return ResponseEntity.ok(Map.of("totalCreated", 0, "duration",
					String.format("%.2fs", (endTime - startTime) / 1000.0), "speed", "N/A", "memoryEstimate", "0 MB"));
		}

		// Use multithreading to generate data in parallel
		int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), 8);
		int batchSize = count / threadCount;
		List<CompletableFuture<Integer>> futures = new ArrayList<>();

		// Create random data generator
		String[] firstNames = { "张", "李", "王", "赵", "陈", "刘", "杨", "黄", "周", "吴" };
		String[] lastNames = { "伟", "强", "军", "明", "刚", "健", "华", "建", "国", "庆" };
		String[] cities = { "BJ", "SH", "GZ", "SZ", "HZ", "NJ", "SU", "WH", "CD", "CQ" };

		for (int t = 0; t < threadCount; t++) {
			int threadIndex = t;
			CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
				int start = threadIndex * batchSize + 1;
				int end = (threadIndex == threadCount - 1) ? count : (threadIndex + 1) * batchSize;
				int created = 0;

				Random random = new Random();
				List<PetType> availableTypes = petTypes.findPetTypes();

				for (int i = start; i <= end; i++) {
					// Generate Owner
					Owner owner = new Owner();
					owner.setFirstName(firstNames[random.nextInt(firstNames.length)]);
					owner.setLastName(lastNames[random.nextInt(lastNames.length)]);
					owner.setAddress("Addr" + i);
					owner.setCity(cities[random.nextInt(cities.length)]);
					owner.setTelephone(String.valueOf(1000000000L + random.nextInt(900000000)));

					// Generate random pets (1-3)
					int petCount = random.nextInt(3) + 1;
					for (int p = 0; p < petCount; p++) {
						Pet pet = new Pet();
						pet.setName("P" + (p + 1) + "-" + owner.getLastName().charAt(0));
						pet.setBirthDate(
								LocalDate.now().minusYears(random.nextInt(5) + 1).minusDays(random.nextInt(365)));
						pet.setType(availableTypes.get(random.nextInt(availableTypes.size())));
						// Clear visits to save memory
						pet.getVisits().clear();

						owner.addPet(pet);
					}

					// Save to memory storage
					this.owners.save(owner);
					created++;
				}

				return created;
			});

			futures.add(future);
		}

		// Wait for all threads to complete
		int totalCreated = futures.stream().mapToInt(future -> {
			try {
				return future.get();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).sum();

		long endTime = System.currentTimeMillis();
		double duration = (endTime - startTime) / 1000.0;
		double speed = totalCreated / duration;

		return ResponseEntity.ok(Map.of("totalCreated", totalCreated, "duration", String.format("%.2fs", duration),
				"speed", String.format("%.0f owners/sec", speed), "memoryEstimate",
				String.format("%.1f MB", totalCreated * 0.001) // 1KB per owner
		));
	}

	@PostMapping("/api/owners/getloadtest/{threads}/{duration}/{qpsLimit}")
	public @ResponseBody ResponseEntity<?> runGetOnlyLoadTest(@PathVariable int threads, @PathVariable int duration,
			@PathVariable int qpsLimit) {
		long startTime = System.nanoTime();

		// Check if there is data
		if (this.owners.findAll().isEmpty()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "No data available. Please generate owners first."));
		}

		// Get total number of users
		int totalOwners = this.owners.findAll().size();

		// Use multithreading for pure GET load testing
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<CompletableFuture<LoadTestResult>> futures = new ArrayList<>();

		// Record statistics for each thread
		for (int t = 0; t < threads; t++) {
			CompletableFuture<LoadTestResult> future = CompletableFuture.supplyAsync(() -> {
				return runGetOnlySingleThreadLoadTest(totalOwners, duration, qpsLimit);
			}, executor);
			futures.add(future);
		}

		// Wait for all threads to complete
		long totalRequests = 0;
		long totalErrors = 0;
		long maxResponseTime = 0;
		long minResponseTime = Long.MAX_VALUE;
		long totalResponseTime = 0;

		try {
			for (CompletableFuture<LoadTestResult> future : futures) {
				LoadTestResult result = future.get();
				totalRequests += result.requests;
				totalErrors += result.errors;
				maxResponseTime = Math.max(maxResponseTime, result.maxResponseTime);
				minResponseTime = Math.min(minResponseTime, result.minResponseTime);
				totalResponseTime += result.totalResponseTime;
			}
		}
		catch (Exception e) {
			executor.shutdownNow();
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "GET load test failed: " + e.getMessage()));
		}

		executor.shutdown();

		long endTime = System.nanoTime();
		double actualDuration = (endTime - startTime) / 1_000_000_000.0;
		double qps = totalRequests / actualDuration;

		Map<String, Object> result = new HashMap<>();
		result.put("totalRequests", totalRequests);
		result.put("totalErrors", totalErrors);
		result.put("successRate", String.format("%.2f%%", (totalRequests - totalErrors) * 100.0 / totalRequests));
		result.put("qps", String.format("%.0f", qps));
		result.put("qpsLimit", qpsLimit);
		result.put("avgResponseTime",
				totalRequests > 0 ? String.format("%.2f μs", totalResponseTime * 1.0 / totalRequests) : "0");
		result.put("minResponseTime", String.format("%.2f μs", minResponseTime * 1.0));
		result.put("maxResponseTime", String.format("%.2f μs", maxResponseTime * 1.0));
		result.put("threads", threads);
		result.put("duration", String.format("%.2fs", actualDuration));
		result.put("dataSize", totalOwners + " owners");
		result.put("testType", "GET Only");

		// Note: Keep response time stats for final analysis - they will accumulate across
		// tests

		return ResponseEntity.ok(result);
	}

	@PostMapping("/api/owners/loadtest/{threads}/{duration}/{qpsLimit}")
	public @ResponseBody ResponseEntity<?> runInMemoryLoadTest(@PathVariable int threads, @PathVariable int duration,
			@PathVariable int qpsLimit) {
		long startTime = System.nanoTime();

		// Check if there is data
		if (this.owners.findAll().isEmpty()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "No data available. Please generate owners first."));
		}

		// Get total number of users
		int totalOwners = this.owners.findAll().size();

		// Use multithreading for load testing
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<CompletableFuture<LoadTestResult>> futures = new ArrayList<>();

		// Record statistics for each thread
		for (int t = 0; t < threads; t++) {
			CompletableFuture<LoadTestResult> future = CompletableFuture.supplyAsync(() -> {
				return runSingleThreadLoadTest(totalOwners, duration, qpsLimit);
			}, executor);
			futures.add(future);
		}

		// Wait for all threads to complete
		long totalRequests = 0;
		long totalErrors = 0;
		long maxResponseTime = 0;
		long minResponseTime = Long.MAX_VALUE;
		long totalResponseTime = 0;

		try {
			for (CompletableFuture<LoadTestResult> future : futures) {
				LoadTestResult result = future.get();
				totalRequests += result.requests;
				totalErrors += result.errors;
				maxResponseTime = Math.max(maxResponseTime, result.maxResponseTime);
				minResponseTime = Math.min(minResponseTime, result.minResponseTime);
				totalResponseTime += result.totalResponseTime;
			}
		}
		catch (Exception e) {
			executor.shutdownNow();
			return ResponseEntity.internalServerError().body(Map.of("error", "Load test failed: " + e.getMessage()));
		}

		executor.shutdown();

		long endTime = System.nanoTime();
		double actualDuration = (endTime - startTime) / 1_000_000_000.0;
		double qps = totalRequests / actualDuration;

		Map<String, Object> result = new HashMap<>();
		result.put("totalRequests", totalRequests);
		result.put("totalErrors", totalErrors);
		result.put("successRate", String.format("%.2f%%", (totalRequests - totalErrors) * 100.0 / totalRequests));
		result.put("qps", String.format("%.0f", qps));
		result.put("qpsLimit", qpsLimit);
		result.put("avgResponseTime",
				totalRequests > 0 ? String.format("%.2f μs", totalResponseTime * 1.0 / totalRequests) : "0");
		result.put("minResponseTime", String.format("%.2f μs", minResponseTime * 1.0));
		result.put("maxResponseTime", String.format("%.2f μs", maxResponseTime * 1.0));
		result.put("threads", threads);
		result.put("duration", String.format("%.2fs", actualDuration));
		result.put("dataSize", totalOwners + " owners");

		// Note: Keep response time stats for final analysis - they will accumulate across
		// tests

		return ResponseEntity.ok(result);
	}

	private static class LoadTestResult {

		long requests = 0;

		long errors = 0;

		long maxResponseTime = 0;

		long minResponseTime = Long.MAX_VALUE;

		long totalResponseTime = 0;

	}

	private LoadTestResult runSingleThreadLoadTest(int totalOwners, int durationSeconds, int qpsLimit) {
		LoadTestResult result = new LoadTestResult();
		Random random = new Random();
		long endTime = System.nanoTime() + (durationSeconds * 1_000_000_000L);

		// QPS control variables
		long intervalNanos = qpsLimit > 0 ? 1_000_000_000L / qpsLimit : 0; // Minimum
																			// interval
																			// between
																			// requests
		long lastRequestTime = 0;

		// To avoid JVM over-optimization, we add some unpredictable operations
		long dummyCounter = 0;

		while (System.nanoTime() < endTime) {
			// QPS control: ensure request interval is not less than the specified minimum
			// interval
			if (qpsLimit > 0) {
				long currentTime = System.nanoTime();
				long timeSinceLastRequest = currentTime - lastRequestTime;
				if (timeSinceLastRequest < intervalNanos) {
					// Need to wait
					long waitTime = intervalNanos - timeSinceLastRequest;
					long waitUntil = currentTime + waitTime;
					while (System.nanoTime() < waitUntil) {
						// Busy wait - this is not a problem in high QPS scenarios because
						// wait time is very short
						Thread.yield();
					}
				}
			}

			long requestStart = System.nanoTime();

			try {
				// 70% GET, 15% POST (INSERT), 15% DELETE - simulate mixed workload
				int operationType = random.nextInt(100);

				if (operationType < 70) {
					// GET operation
					int ownerId = random.nextInt(totalOwners) + 1; // 1-based ID
					Owner owner = this.owners.findById(ownerId).orElse(null);
					if (owner != null) {
						// Perform more realistic computation operations to avoid JVM
						// optimization
						String firstName = owner.getFirstName();
						String lastName = owner.getLastName();
						String address = owner.getCity();

						// Calculate string hash values (with actual computation overhead)
						int hash = firstName.hashCode() + lastName.hashCode() + address.hashCode();
						dummyCounter += hash; // Use result to avoid being optimized away

						// Iterate through pet list and perform calculations
						for (Pet pet : owner.getPets()) {
							String petName = pet.getName();
							String typeName = pet.getType().getName();
							hash = petName.hashCode() + typeName.hashCode();
							dummyCounter += hash;
						}

						// Add some random calculations to simulate business logic
						if ((dummyCounter & 0xFF) == 0) {
							// Rarely executed branch, avoid being optimized
							dummyCounter += Math.abs(ownerId);
						}
					}
				}
				else if (operationType < 85) {
					// POST operation (INSERT) - create new user
					Owner newOwner = new Owner();
					newOwner.setFirstName("LoadTest" + random.nextInt(1000000));
					newOwner.setLastName("User");
					newOwner.setAddress("Test Address");
					newOwner.setCity("TestCity");
					newOwner.setTelephone(String.valueOf(1000000000L + random.nextInt(900000000)));

					this.owners.save(newOwner);
					dummyCounter += newOwner.getId(); // Use ID to avoid optimization
				}
				else {
					// DELETE operation - delete random user
					int ownerId = random.nextInt(totalOwners) + 1; // 1-based ID
					try {
						Owner ownerToDelete = this.owners.findById(ownerId).orElse(null);
						if (ownerToDelete != null) {
							this.owners.delete(ownerToDelete);
							dummyCounter += ownerId; // Use ID to avoid optimization
						}
					}
					catch (Exception e) {
						// Delete non-existent user, ignore error
					}
				}
			}
			catch (Exception e) {
				result.errors++;
			}
			long requestEnd = System.nanoTime();

			// Calculate response time (microseconds)
			long responseTime = (requestEnd - requestStart) / 1000; // Convert nanoseconds
																	// to microseconds
			result.requests++;
			result.maxResponseTime = Math.max(result.maxResponseTime, responseTime);
			result.minResponseTime = Math.min(result.minResponseTime, responseTime);
			result.totalResponseTime += responseTime;

			// Sample 1% of requests for detailed statistics
			if (random.nextInt(100) < 1) {
				recordResponseTime(responseTime);
			}

			// Update last request time
			lastRequestTime = System.nanoTime();
		}

		// Print dummyCounter to ensure calculations are not optimized away
		System.out.println("Thread completed with dummyCounter: " + dummyCounter);

		return result;
	}

	private LoadTestResult runGetOnlySingleThreadLoadTest(int totalOwners, int durationSeconds, int qpsLimit) {
		LoadTestResult result = new LoadTestResult();
		Random random = new Random();
		long endTime = System.nanoTime() + (durationSeconds * 1_000_000_000L);

		// QPS control variables
		long intervalNanos = qpsLimit > 0 ? 1_000_000_000L / qpsLimit : 0; // Minimum
																			// interval
																			// between
																			// requests
		long lastRequestTime = 0;

		// To avoid JVM over-optimization, we add some unpredictable operations
		long dummyCounter = 0;

		while (System.nanoTime() < endTime) {
			// QPS control: ensure request interval is not less than the specified minimum
			// interval
			if (qpsLimit > 0) {
				long currentTime = System.nanoTime();
				long timeSinceLastRequest = currentTime - lastRequestTime;
				if (timeSinceLastRequest < intervalNanos) {
					// Need to wait
					long waitTime = intervalNanos - timeSinceLastRequest;
					long waitUntil = currentTime + waitTime;
					while (System.nanoTime() < waitUntil) {
						// Busy wait - this is not a problem in high QPS scenarios because
						// wait time is very short
						Thread.yield();
					}
				}
			}

			long requestStart = System.nanoTime();

			try {
				// Pure GET operation - simulate read workload
				int ownerId = random.nextInt(totalOwners) + 1; // 1-based ID
				Owner owner = this.owners.findById(ownerId).orElse(null);
				if (owner != null) {
					// Perform more realistic computation operations to avoid JVM
					// optimization
					String firstName = owner.getFirstName();
					String lastName = owner.getLastName();
					String address = owner.getCity();

					// Calculate string hash values (with actual computation overhead)
					int hash = firstName.hashCode() + lastName.hashCode() + address.hashCode();
					dummyCounter += hash; // Use result to avoid being optimized away

					// Iterate through pet list and perform calculations
					for (Pet pet : owner.getPets()) {
						String petName = pet.getName();
						String typeName = pet.getType().getName();
						hash = petName.hashCode() + typeName.hashCode();
						dummyCounter += hash;
					}

					// Add some random calculations to simulate business logic
					if ((dummyCounter & 0xFF) == 0) {
						// Rarely executed branch, avoid being optimized
						dummyCounter += Math.abs(ownerId);
					}
				}
			}
			catch (Exception e) {
				result.errors++;
			}
			long requestEnd = System.nanoTime();

			// Calculate response time (microseconds)
			long responseTime = (requestEnd - requestStart) / 1000; // Convert nanoseconds
																	// to microseconds
			result.requests++;
			result.maxResponseTime = Math.max(result.maxResponseTime, responseTime);
			result.minResponseTime = Math.min(result.minResponseTime, result.minResponseTime);
			result.totalResponseTime += responseTime;

			lastRequestTime = System.nanoTime();
		}

		return result;
	}

	@PostMapping("/api/owners")
	public @ResponseBody ResponseEntity<?> createOwnerWithRandomPetsApi(@Valid @RequestBody Owner owner,
			BindingResult result) {
		long startTime = System.nanoTime();
		try {
			if (result.hasErrors()) {
				long responseTime = (System.nanoTime() - startTime) / 1000;
				recordResponseTime(responseTime);
				return ResponseEntity.badRequest().body("Invalid owner data: " + result.getAllErrors());
			}

			// Check dataset size limit to prevent unbounded memory growth
			long currentSize = this.owners.count();
			if (currentSize >= MAX_DATASET_SIZE) {
				long responseTime = (System.nanoTime() - startTime) / 1000;
				recordResponseTime(responseTime);
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("error",
							"Dataset size limit reached (" + MAX_DATASET_SIZE + " owners). "
									+ "Cannot create more owners to prevent memory exhaustion. "
									+ "Consider deleting some owners first or restarting the application."));
			}

			// Generate random number of pets (1-3, reduced for memory optimization)
			int numberOfPets = random.nextInt(3) + 1;

			// Get all available pet types
			List<PetType> availableTypes = petTypes.findPetTypes();

			// Create random pets for the owner (optimized for memory)
			for (int i = 0; i < numberOfPets; i++) {
				Pet pet = new Pet();
				// Shortened pet name to reduce memory
				pet.setName("P" + (i + 1) + "-" + owner.getLastName().charAt(0));

				// Random birth date between 1-5 years ago (reduced range)
				int yearsAgo = random.nextInt(5) + 1;
				LocalDate birthDate = LocalDate.now().minusYears(yearsAgo).minusDays(random.nextInt(365));
				pet.setBirthDate(birthDate);

				// Random pet type
				PetType randomType = availableTypes.get(random.nextInt(availableTypes.size()));
				pet.setType(randomType);

				// Clear visits to reduce memory (no visits needed for memory test)
				pet.getVisits().clear();

				// Add pet to owner
				owner.addPet(pet);
			}

			Owner savedOwner = this.owners.save(owner);
			long responseTime = (System.nanoTime() - startTime) / 1000; // Convert to
																		// microseconds
			recordResponseTime(responseTime);
			return ResponseEntity.ok(savedOwner);
		}
		catch (Exception e) {
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			throw e;
		}
	}

	/**
	 * REST API endpoint to delete an owner and all their pets
	 * @param ownerId the ID of the owner to delete
	 * @return response indicating success or failure
	 */
	@DeleteMapping("/api/owners/{ownerId}")
	public ResponseEntity<Void> deleteOwnerAndPetsApi(@PathVariable("ownerId") int ownerId) {
		long startTime = System.nanoTime();
		try {
			Optional<Owner> optionalOwner = this.owners.findById(ownerId);
			ResponseEntity<Void> response;
			if (optionalOwner.isPresent()) {
				Owner owner = optionalOwner.get();
				// Delete owner and all associated pets (cascade delete)
				this.owners.delete(owner);
				response = ResponseEntity.noContent().build();
			}
			else {
				response = ResponseEntity.notFound().build();
			}
			long responseTime = (System.nanoTime() - startTime) / 1000; // Convert to
																		// microseconds
			recordResponseTime(responseTime);
			return response;
		}
		catch (Exception e) {
			long responseTime = (System.nanoTime() - startTime) / 1000;
			recordResponseTime(responseTime);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastNameStartingWith(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		if (!Objects.equals(owner.getId(), ownerId)) {
			result.rejectValue("id", "mismatch", "The owner ID in the form does not match the URL.");
			redirectAttributes.addFlashAttribute("error", "Owner ID mismatch. Please try again.");
			return "redirect:/owners/{ownerId}/edit";
		}

		owner.setId(ownerId);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		mav.addObject(owner);
		return mav;
	}

	/**
	 * Handler for deleting an owner.
	 * @param ownerId the ID of the owner to delete
	 * @return ResponseEntity with HTTP status
	 */
	@DeleteMapping("/owners/{ownerId}")
	public ResponseEntity<Void> deleteOwner(@PathVariable("ownerId") int ownerId) {
		try {
			Optional<Owner> optionalOwner = this.owners.findById(ownerId);
			if (optionalOwner.isPresent()) {
				// Load the owner with all associations to ensure cascade delete works
				// properly
				Owner owner = optionalOwner.get();
				this.owners.delete(owner);
				return ResponseEntity.noContent().build();
			}
			return ResponseEntity.notFound().build();
		}
		catch (DataIntegrityViolationException e) {
			// Handle database constraint violations gracefully
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		catch (Exception e) {
			// For other exceptions, return server error
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

}
