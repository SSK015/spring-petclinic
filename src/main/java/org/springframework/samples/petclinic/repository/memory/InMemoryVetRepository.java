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
package org.springframework.samples.petclinic.repository.memory;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory implementation of VetRepository.
 */
@Repository
public class InMemoryVetRepository implements VetRepository {

	private final InMemoryRepository<Vet, Integer> vetStorage = new InMemoryRepository<Vet, Integer>() {
	};

	@Override
	@Cacheable("vets")
	public Collection<Vet> findAll() throws DataAccessException {
		return new ArrayList<>(vetStorage.findAll());
	}

	@Override
	@Cacheable("vets")
	public Page<Vet> findAll(Pageable pageable) throws DataAccessException {
		List<Vet> allVets = vetStorage.findAll();
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), allVets.size());

		if (start >= allVets.size()) {
			return new PageImpl<>(List.of(), pageable, allVets.size());
		}

		List<Vet> pageContent = allVets.subList(start, end);
		return new PageImpl<>(pageContent, pageable, allVets.size());
	}

	// Initialize with some sample vets
	{
		// Create specialties
		Specialty radiology = new Specialty();
		radiology.setId(1);
		radiology.setName("radiology");

		Specialty surgery = new Specialty();
		surgery.setId(2);
		surgery.setName("surgery");

		Specialty dentistry = new Specialty();
		dentistry.setId(3);
		dentistry.setName("dentistry");

		// Create vets
		Vet vet1 = new Vet();
		vet1.setId(1);
		vet1.setFirstName("James");
		vet1.setLastName("Carter");
		vet1.addSpecialty(radiology);
		vetStorage.save(vet1);

		Vet vet2 = new Vet();
		vet2.setId(2);
		vet2.setFirstName("Helen");
		vet2.setLastName("Leary");
		vet2.addSpecialty(surgery);
		vetStorage.save(vet2);

		Vet vet3 = new Vet();
		vet3.setId(3);
		vet3.setFirstName("Linda");
		vet3.setLastName("Douglas");
		vet3.addSpecialty(dentistry);
		vet3.addSpecialty(surgery);
		vetStorage.save(vet3);

		Vet vet4 = new Vet();
		vet4.setId(4);
		vet4.setFirstName("Rafael");
		vet4.setLastName("Ortega");
		vet4.addSpecialty(surgery);
		vetStorage.save(vet4);

		Vet vet5 = new Vet();
		vet5.setId(5);
		vet5.setFirstName("Henry");
		vet5.setLastName("Stevens");
		vet5.addSpecialty(radiology);
		vetStorage.save(vet5);

		Vet vet6 = new Vet();
		vet6.setId(6);
		vet6.setFirstName("Sharon");
		vet6.setLastName("Jenkins");
		vetStorage.save(vet6);
	}

}
