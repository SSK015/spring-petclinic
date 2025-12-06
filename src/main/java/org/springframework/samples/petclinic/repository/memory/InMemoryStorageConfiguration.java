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

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.PetTypeRepository;
import org.springframework.samples.petclinic.vet.VetRepository;

/**
 * Configuration for in-memory storage repositories. Enable with: app.storage=memory This
 * disables JPA and DataSource auto-configuration.
 */
@Configuration
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@ConditionalOnProperty(name = "app.storage", havingValue = "memory")
public class InMemoryStorageConfiguration {

	@Bean
	@Primary
	public OwnerRepository ownerRepository() {
		return new InMemoryOwnerRepository();
	}

	@Bean
	@Primary
	public PetTypeRepository petTypeRepository() {
		return new InMemoryPetTypeRepository();
	}

	@Bean
	@Primary
	public VetRepository vetRepository() {
		return new InMemoryVetRepository();
	}

}
