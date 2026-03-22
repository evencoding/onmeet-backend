package model

import (
	"time"

	"gorm.io/datatypes"
	"gorm.io/gorm"
)

// FileMetadata 구조체는 데이터베이스의 file_metadata 테이블과 매핑되는 모델입니다.
// Java/Kotlin의 @Entity 클래스와 같은 역할을 합니다.
type FileMetadata struct {
	// 백틱(`) 안에 있는 것은 'Struct Tag'라고 하며, GORM(ORM) 설정이나 JSON 변환 설정을 담습니다.
	// gorm:"primaryKey"는 JPA의 @Id와 같고, json:"id"는 Jackson의 @JsonProperty("id")와 같습니다.
	ID               uint   `gorm:"primaryKey" json:"id" example:"1"`
	FileName         string `gorm:"not null" json:"fileName" example:"550e8400-e29b-41d4-a716-446655440000.jpg"`
	Category         string `gorm:"not null" json:"category" example:"TEAM_PROFILE"`
	OriginalFileName string `gorm:"not null" json:"originalFileName" example:"photo.jpg"`
	S3URL            string `gorm:"not null" json:"s3Url" example:"https://cdn.onmeet.cloud/USER/123/TEAM_PROFILE/550e8400.jpg"`
	FileSize         int64  `gorm:"not null" json:"fileSize" example:"204800"`
	ContentType      string `gorm:"not null" json:"contentType" example:"image/jpeg"`
	OwnerType        string `gorm:"not null;default:'USER'" json:"ownerType" example:"USER"`
	OwnerID          string `gorm:"not null" json:"ownerId" example:"123"`
	// *int64처럼 포인터를 쓰는 이유는 DB의 NULL 값을 허용하기 위해서입니다. (Java의 클래스 타입 Integer와 유사)
	UploaderID *int64 `json:"uploaderId" example:"42"`
	// datatypes.JSON은 PostgreSQL의 jsonb 타입을 지원하기 위한 특수 타입입니다.
	ExtraInfo datatypes.JSON `gorm:"type:jsonb" json:"extraInfo" swaggertype:"object"`
	CreatedAt time.Time      `json:"createdAt" example:"2024-03-10T09:00:00Z"`
	UpdatedAt time.Time      `json:"updatedAt" example:"2024-03-10T09:00:00Z"`
	// gorm.DeletedAt은 'Soft Delete'(논리 삭제) 기능을 지원합니다. (JPA의 @SQLDelete와 비유 가능)
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}

// TableName은 GORM에서 이 구조체가 사용할 테이블 이름을 명시적으로 지정합니다.
// 지정하지 않으면 구조체 이름의 복수형(file_metadata)이 기본값이 됩니다.
func (FileMetadata) TableName() string {
	return "file_metadata"
}
