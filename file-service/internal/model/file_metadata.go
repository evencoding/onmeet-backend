package model

import (
	"time"

	"gorm.io/datatypes"
	"gorm.io/gorm"
)

type FileMetadata struct {
	ID               uint           `gorm:"primaryKey" json:"id"`
	FileName         string         `gorm:"not null" json:"fileName"`
	Category         string         `gorm:"not null" json:"category"`
	OriginalFileName string         `gorm:"not null" json:"originalFileName"`
	S3URL            string         `gorm:"not null" json:"s3Url"`
	FileSize         int64          `gorm:"not null" json:"fileSize"`
	ContentType      string         `gorm:"not null" json:"contentType"`
	OwnerType        string         `gorm:"not null;default:'USER'" json:"ownerType"`
	OwnerID          string         `gorm:"not null" json:"ownerId"`
	UploaderID       *int64         `json:"uploaderId"`
	ExtraInfo        datatypes.JSON `gorm:"type:jsonb" json:"extraInfo"`
	CreatedAt        time.Time      `json:"createdAt"`
	UpdatedAt        time.Time      `json:"updatedAt"`
	DeletedAt        gorm.DeletedAt `gorm:"index" json:"-"`
}

func (FileMetadata) TableName() string {
	return "file_metadata"
}
