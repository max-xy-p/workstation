from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.development_snapshot_response import DevelopmentSnapshotResponse
    from ..models.repository_response import RepositoryResponse


T = TypeVar("T", bound="ProjectDevelopmentResponse")


@_attrs_define
class ProjectDevelopmentResponse:
    """
    Attributes:
        repositories (list[RepositoryResponse]):
        snapshots (list[DevelopmentSnapshotResponse]):
    """

    repositories: list[RepositoryResponse]
    snapshots: list[DevelopmentSnapshotResponse]

    def to_dict(self) -> dict[str, Any]:
        repositories = []
        for repositories_item_data in self.repositories:
            repositories_item = repositories_item_data.to_dict()
            repositories.append(repositories_item)

        snapshots = []
        for snapshots_item_data in self.snapshots:
            snapshots_item = snapshots_item_data.to_dict()
            snapshots.append(snapshots_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "repositories": repositories,
                "snapshots": snapshots,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.development_snapshot_response import DevelopmentSnapshotResponse  # noqa: PLC0415
        from ..models.repository_response import RepositoryResponse  # noqa: PLC0415

        d = dict(src_dict)
        repositories = []
        _repositories = d.pop("repositories")
        for repositories_item_data in _repositories:
            repositories_item = RepositoryResponse.from_dict(repositories_item_data)

            repositories.append(repositories_item)

        snapshots = []
        _snapshots = d.pop("snapshots")
        for snapshots_item_data in _snapshots:
            snapshots_item = DevelopmentSnapshotResponse.from_dict(snapshots_item_data)

            snapshots.append(snapshots_item)

        project_development_response = cls(
            repositories=repositories,
            snapshots=snapshots,
        )

        return project_development_response
