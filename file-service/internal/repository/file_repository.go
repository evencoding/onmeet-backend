package repository

import (
	"errors"

	"com.onmeet.file/internal/model"
	"gorm.io/gorm"
)

// FileRepository 인터페이스는 파일 메타데이터 관리를 위한 추상적인 명세입니다.
// Java의 Repository Interface(Spring Data JPA)와 같은 역할을 합니다.
type FileRepository interface {
	Save(metadata *model.FileMetadata) error
	FindByID(id uint) (*model.FileMetadata, error)
	Delete(id uint) error
	FindByUploaderAndCategory(uploaderId int64, category string) ([]*model.FileMetadata, error)
}

// postgresFileRepository는 FileRepository 인터페이스의 실제 구현체입니다.
// 필드명이 소문자(p)로 시작하므로 이 패키지 외부에서는 접근할 수 없습니다. (Private)
type postgresFileRepository struct {
	db *gorm.DB
}

// NewPostgresFileRepository는 리포지토리 구현체의 인스턴스를 생성하는 '생성자' 역할을 합니다.
// Spring의 Bean 생성 과정과 비슷하지만, Go에서는 이렇게 수동으로 생성 함수를 만듭니다.
func NewPostgresFileRepository(db *gorm.DB) FileRepository {
	// 인터페이스 타입(FileRepository)을 반환하므로, 실제 구현체인 postgresFileRepository의 주소(&)를 넘깁니다.
	return &postgresFileRepository{db: db}
}

// (r *postgresFileRepository) 구문은 이 함수가 postgresFileRepository의 메서드임을 정의합니다.
// Java의 인스턴스 메서드(this)와 같으며, Go에서는 'Receiver'라고 부릅니다.
func (r *postgresFileRepository) Save(metadata *model.FileMetadata) error {
	// GORM의 Save를 호출하고 발생한 에러를 반환합니다.
	return r.db.Save(metadata).Error
}

func (r *postgresFileRepository) FindByID(id uint) (*model.FileMetadata, error) {
	var metadata model.FileMetadata
	// First는 단 건 조회를 수행합니다. JPA의 findById()와 유사합니다.
	// &metadata는 조회된 결과를 해당 변수에 담아달라는 뜻(주소 전달)입니다.
	if err := r.db.First(&metadata, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, model.ErrDBNotFound
		}
		return nil, model.NewAppError(model.CodeDBSaveFail, 500, "파일 메타데이터 DB 조회에 실패했습니다")
	}
	return &metadata, nil
}

func (r *postgresFileRepository) Delete(id uint) error {
	// Delete는 데이터를 삭제합니다. model.FileMetadata{}를 넘겨 테이블을 지정합니다.
	return r.db.Delete(&model.FileMetadata{}, id).Error
}

func (r *postgresFileRepository) FindByUploaderAndCategory(uploaderId int64, category string) ([]*model.FileMetadata, error) {
	var results []*model.FileMetadata
	err := r.db.Where("uploader_id = ? AND category = ?", uploaderId, category).Find(&results).Error
	return results, err
}
