package by.timofeyzaytsev.limitservice.repository;

import by.timofeyzaytsev.limitservice.model.Limit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LimitRepository extends JpaRepository<Limit, UUID> {

}
