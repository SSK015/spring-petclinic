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

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory implementation of OwnerRepository.
 */
@Repository
public class InMemoryOwnerRepository extends InMemoryRepository<Owner, Integer> implements OwnerRepository {

	@Override
	public Page<Owner> findByLastNameStartingWith(String lastName, Pageable pageable) {
		List<Owner> filtered = storage.values()
			.stream()
			.filter(owner -> owner.getLastName().startsWith(lastName))
			.sorted(Comparator.comparing(Owner::getLastName))
			.collect(Collectors.toList());

		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), filtered.size());

		if (start >= filtered.size()) {
			return new PageImpl<>(List.of(), pageable, filtered.size());
		}

		return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
	}

	@Override
	public List<Owner> findAll() {
		return storage.values().stream().sorted(Comparator.comparing(Owner::getLastName)).collect(Collectors.toList());
	}

	@Override
	public List<Owner> findAll(Sort sort) {
		return findAll(); // Simplified implementation
	}

	@Override
	public List<Owner> findAllById(Iterable<Integer> ids) {
		List<Integer> idList = new ArrayList<>();
		ids.forEach(idList::add);
		return storage.entrySet()
			.stream()
			.filter(entry -> idList.contains(entry.getKey()))
			.map(entry -> entry.getValue())
			.collect(Collectors.toList());
	}

	@Override
	public <S extends Owner> List<S> saveAll(Iterable<S> entities) {
		List<S> saved = new ArrayList<>();
		entities.forEach(entity -> saved.add((S) save(entity)));
		return saved;
	}

	@Override
	public void deleteAllById(Iterable<? extends Integer> ids) {
		ids.forEach(storage::remove);
	}

	@Override
	public void deleteAll(Iterable<? extends Owner> entities) {
		entities.forEach(entity -> storage.remove(entity.getId()));
	}

	@Override
	public Owner getReferenceById(Integer id) {
		return findById(id).orElseThrow(() -> new RuntimeException("Entity not found"));
	}

	@Override
	public Owner getById(Integer id) {
		return findById(id).orElse(null);
	}

	@Override
	public Owner getOne(Integer id) {
		return getById(id);
	}

	@Override
	public Owner save(Owner entity) {
		if (entity.isNew()) {
			entity.setId(generateId());
		}
		storage.put(entity.getId(), entity);
		return entity;
	}

	@Override
	public void deleteAllInBatch() {
		deleteAll();
	}

	@Override
	public void deleteAllByIdInBatch(Iterable<Integer> ids) {
		deleteAllById(ids);
	}

	@Override
	public void deleteAllInBatch(Iterable<Owner> entities) {
		deleteAll(entities);
	}

	@Override
	public Owner saveAndFlush(Owner entity) {
		return save(entity);
	}

	@Override
	public org.springframework.data.domain.Page<Owner> findAll(org.springframework.data.domain.Pageable pageable) {
		List<Owner> all = findAll();
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), all.size());

		if (start >= all.size()) {
			return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, all.size());
		}

		List<Owner> pageContent = all.subList(start, end);
		return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, all.size());
	}

	@Override
	public <S extends Owner> List<S> saveAllAndFlush(Iterable<S> entities) {
		return saveAll(entities);
	}

	@Override
	public void flush() {
		// No-op for in-memory storage
	}

	@Override
	public <S extends Owner> List<S> findAll(Example<S> example, Sort sort) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends Owner> List<S> findAll(Example<S> example) {
		// Simplified implementation - not used in the application
		return new ArrayList<>();
	}

	@Override
	public <S extends Owner> Optional<S> findOne(Example<S> example) {
		// Simplified implementation - not used in the application
		return Optional.empty();
	}

	@Override
	public <S extends Owner> Page<S> findAll(Example<S> example, Pageable pageable) {
		// Simplified implementation - not used in the application
		return new PageImpl<>(new ArrayList<>(), pageable, 0);
	}

	@Override
	public <S extends Owner> long count(Example<S> example) {
		// Simplified implementation - not used in the application
		return 0;
	}

	@Override
	public <S extends Owner> boolean exists(Example<S> example) {
		// Simplified implementation - not used in the application
		return false;
	}

	@Override
	public <S extends Owner, R> R findBy(Example<S> example,
			java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
		// Simplified implementation - not used in the application
		return null;
	}

}
