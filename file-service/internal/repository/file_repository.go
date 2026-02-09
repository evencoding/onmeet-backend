package repository

import (
	"com.onmeet.file/internal/model"
	"gorm.io/gorm"
)

type FileRepository interface {
	Save(metadata *model.FileMetadata) error
	FindByID(id uint) (*model.FileMetadata, error)
	Delete(id uint) error
}

type postgresFileRepository struct {
	db *gorm.DB
}

func NewPostgresFileRepository(db *gorm.DB) FileRepository {
	return &postgresFileRepository{db: db}
}

func (r *postgresFileRepository) Save(metadata *model.FileMetadata) error {
	return r.db.Save(metadata).Error
}

func (r *postgresFileRepository) FindByID(id uint) (*model.FileMetadata, error) {
	var metadata model.FileMetadata
	if err := r.db.First(&metadata, id).Error; err != nil {
		return nil, err
	}
	return &metadata, nil
}

func (r *postgresFileRepository) Delete(id uint) error {
	return r.db.Delete(&model.FileMetadata{}, id).Error
}
